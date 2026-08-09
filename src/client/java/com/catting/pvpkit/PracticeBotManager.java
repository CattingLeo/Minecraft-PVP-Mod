package com.catting.pvpkit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * /practicebot -- summons a full-netherite, effectively-unkillable combat
 * dummy wearing your own name and skin, standing exactly where you're
 * standing (via Fabric API's FakePlayer, a real ServerPlayer-derived entity
 * with a working stand-in network listener -- not a genuine connected
 * player, and not something this mod built from scratch: FakePlayer is
 * Fabric API's own sanctioned tool for exactly this, already a transitive
 * dependency via fabric-events-interaction-v0).
 * /practicebot shield makes it hold its shield up permanently.
 * /practicebot remove despawns it.
 *
 * SINGLEPLAYER / WORLD-HOST ONLY. Spawning a real, hittable entity needs
 * server authority, and this is a client-side-only mod -- it only has that
 * authority when you ARE the server (Minecraft#getSingleplayerServer() is
 * non-null in true singleplayer and for whoever used "Open to LAN"; null
 * for anyone who just joined someone else's world). A friend connected to
 * your LAN world CAN fight the bot once you've summoned it -- it's a real
 * entity tracked by the server and synced to them the normal way -- they
 * just can't summon their own from their side.
 *
 * All world/entity mutation runs inside server.execute(...), NOT directly
 * on the client/render thread the command handler fires on -- even in
 * singleplayer the integrated server runs on its own thread, and C2ME's
 * threading-safety mixin (one of this user's other installed mods)
 * correctly throws ConcurrentModificationException ("Async entity load")
 * for an illegal cross-thread chunk/entity add otherwise.
 *
 * Uses a fixed synthetic UUID (nameUUIDFromBytes, NOT your real account
 * UUID -- two entities can never share one) so repeat /practicebot calls
 * reuse and reposition the same FakePlayer instance rather than piling up
 * duplicates: Fabric caches FakePlayer.get() results keyed by (level,
 * profile). If that instance is already tracked in the level, it's discarded
 * and re-added so vanilla re-pairs it to every nearby client -- see
 * performSummon for why "already tracked" server-side does NOT mean the
 * client actually has the entity.
 *
 * A player-type entity is silently discarded client-side ("Skipping Entity
 * with id entity.minecraft.player") if the client hasn't already been told
 * this UUID's GameProfile -- normally sent by PlayerList#placeNewPlayer
 * during a real player's join, which this FakePlayer never goes through.
 * ClientboundPlayerInfoUpdatePacket.createPlayerInitializing replicates
 * that same initial-info packet so the client accepts the entity.
 *
 * Not setInvulnerable(true), and no Resistance/Regeneration either -- all of
 * those skip or blunt the vanilla hurt pipeline (setInvulnerable skips it
 * outright, including the knockback applied in the same call; Resistance 255
 * flattens damage to zero, which suppresses knockback and hit reactions the
 * same way). The bot instead survives by popping REAL totems, restocked in
 * its offhand every tick by PracticeBotAi -- so hits, damage, knockback,
 * sounds and the totem pop itself all behave exactly as they do against a
 * real player, and you get to practise actually bursting through a totem.
 *
 * The bot does NOT survive a world rejoin on its own -- Player#shouldBeSaved()
 * unconditionally returns false (verified in the class bytecode), excluding
 * every player-type entity, FakePlayer included, from normal chunk-based
 * saving; combined with FakePlayer's own cache being purely in-memory, the
 * whole entity is gone after a restart, not just its effects. PracticeBotState
 * persists whether one was active (and where) to config/practicebot.json,
 * and the SERVER_STARTED listener below re-summons it automatically.
 */
public final class PracticeBotManager {

    private static final UUID BOT_UUID =
            UUID.nameUUIDFromBytes("pvpkit:practicebot".getBytes(StandardCharsets.UTF_8));

    /** Id of the modifier that cancels the armour's knockback resistance -- see performSummon. */
    private static final Identifier NO_KNOCKBACK_RESISTANCE =
            Identifier.fromNamespaceAndPath(PvpKitClient.MOD_ID, "bot_no_knockback_resistance");

    private static FakePlayer bot;
    private static PracticeBotAi.Mode mode = PracticeBotAi.Mode.IDLE;

    private PracticeBotManager() {
    }

    /** True only for the one FakePlayer this mod owns -- see FakePlayerTickMixin for why the scope matters. */
    public static boolean isPracticeBot(Object entity) {
        return bot != null && bot == entity;
    }

    /** A tick path threw for the bot; log once rather than killing the server tick loop. */
    public static void onTickError(Exception e) {
        if (tickErrorLogged) return;
        tickErrorLogged = true;
        LOGGER.warn("[pvpkit] practice bot tick threw (suppressed, logged once)", e);
    }

    private static boolean tickErrorLogged;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("pvpkit");

    // ---- TEMPORARY DIAGNOSTICS: proves FakePlayerTickMixin is actually running ----
    private static int realTickCount;

    /** Called from FakePlayerTickMixin each time the REAL player tick runs for our bot. */
    public static void countRealTick() {
        realTickCount++;
    }

    public static int tickCount() {
        return realTickCount;
    }

    /** TEMPORARY: KnockbackProbeMixin routes vanilla knockback() calls here. */
    public static void logKnockback(String msg) {
        LOGGER.info("[practicebot] {}", msg);
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            var root = ClientCommands.literal("practicebot")
                    .executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.IDLE))
                    .then(ClientCommands.literal("remove").executes(PracticeBotManager::remove));
            root = root.then(ClientCommands.literal("shield").executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.SHIELD)));
            root = root.then(ClientCommands.literal("defend").executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.DEFEND)));
            root = root.then(ClientCommands.literal("unmoveable").executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.UNMOVEABLE)));
            dispatcher.register(root);
        });

        // Runs on the server thread already (this event fires there), so no
        // server.execute() hop needed.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (bot == null || !bot.isAlive()) return;
            ServerLevel level = (ServerLevel) bot.level();
            PracticeBotAi.tick(bot, mode, level);

            // NOTE: the player-info packet a client needs before it will accept this
            // entity is deliberately NOT re-broadcast here on a timer any more. That was
            // a race, not a fix: the entity tracker re-adds at arbitrary moments, so any
            // re-add landing in the gap between two broadcasts got rejected client-side
            // ("Server attempted to add player prior to sending player info") and the bot
            // silently vanished. BotTrackingMixin now sends it at the HEAD of
            // ServerEntity#addPairing instead -- immediately before each spawn packet,
            // per player, on every add. See that mixin's javadoc.
        });

        ServerLifecycleEvents.SERVER_STARTED.register(PracticeBotManager::restoreOnServerStart);
    }

    private static int summon(FabricClientCommandSource source, PracticeBotAi.Mode requested) {
        Minecraft mc = source.getClient();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            source.sendError(Component.literal(
                    "Practice bot needs you to be the world host (singleplayer, or you used \"Open to LAN\") "
                    + "-- can't summon into someone else's world from here."));
            return 0;
        }

        // Captured here, on the client thread, before hopping to the server thread below.
        var player = source.getPlayer();
        ResourceKey<Level> dimension = source.getLevel().dimension();
        GameProfile real = mc.getGameProfile();
        Vec3 spawnPos = player.position();
        float yaw = player.getYRot() + 180.0f;
        Component doneMessage = Component.literal(requested == PracticeBotAi.Mode.IDLE
                ? "Practice bot summoned."
                : "Practice bot summoned -- mode: " + requested.label + ".");

        server.execute(() -> performSummon(server, dimension, real, spawnPos, yaw, requested,
                () -> {
                    PracticeBotState state = PracticeBotState.get();
                    state.active = true;
                    state.mode = requested.name();
                    state.dimension = dimension.identifier().toString();
                    state.x = spawnPos.x;
                    state.y = spawnPos.y;
                    state.z = spawnPos.z;
                    state.yaw = yaw;
                    PracticeBotState.save();
                    mc.execute(() -> source.sendFeedback(doneMessage));
                },
                error -> mc.execute(() -> source.sendError(error))));
        return 1;
    }

    /** Re-summons the bot on world load if one was active when you last quit -- see class javadoc. */
    private static void restoreOnServerStart(MinecraftServer server) {
        PracticeBotState state = PracticeBotState.get();
        if (!state.active) return;
        Minecraft mc = Minecraft.getInstance();
        GameProfile real = mc.getGameProfile();
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(state.dimension));
        Vec3 spawnPos = new Vec3(state.x, state.y, state.z);
        PracticeBotAi.Mode saved;
        try {
            saved = PracticeBotAi.Mode.valueOf(state.mode);
        } catch (IllegalArgumentException e) {
            saved = PracticeBotAi.Mode.IDLE; // unknown/renamed mode in the json -- degrade quietly
        }
        PracticeBotAi.Mode restored = saved;
        float yaw = state.yaw;

        server.execute(() -> performSummon(server, dimension, real, spawnPos, yaw, restored,
                () -> { /* silent -- this runs automatically, not from a command the player typed */ },
                error -> { /* nothing to report an error to here either */ }));
    }

    /** Actual entity creation/equip/positioning, shared by the command path and the world-load restore path. Must run inside server.execute(...). */
    private static void performSummon(MinecraftServer server, ResourceKey<Level> dimension, GameProfile real,
                                       Vec3 spawnPos, float yaw, PracticeBotAi.Mode requested,
                                       Runnable onSuccess, Consumer<Component> onError) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            onError.accept(Component.literal("Couldn't find that dimension server-side."));
            return;
        }

        GameProfile fake = new GameProfile(BOT_UUID, real.name(), new PropertyMap(real.properties()));
        FakePlayer newBot = FakePlayer.get(level, fake);
        boolean alreadyTracked = level.getEntity(BOT_UUID) != null;

        RegistryAccess registries = level.registryAccess();
        newBot.setItemSlot(EquipmentSlot.HEAD, armor(registries, Items.NETHERITE_HELMET));
        newBot.setItemSlot(EquipmentSlot.LEGS, armor(registries, Items.NETHERITE_LEGGINGS));
        newBot.setItemSlot(EquipmentSlot.FEET, armor(registries, Items.NETHERITE_BOOTS));
        newBot.setItemSlot(EquipmentSlot.CHEST, armor(registries, Items.NETHERITE_CHESTPLATE));
        // Shield mode blocks with the shield in the MAIN hand, so the offhand is free to
        // permanently hold a totem -- which is exactly how a real crystal PvP player is
        // kitted (sword + totem), and is what lets the bot survive by popping totems
        // instead of being handed artificial damage immunity. Shield mode never attacks,
        // so it loses nothing by not holding the sword.
        if (requested == PracticeBotAi.Mode.SHIELD) {
            newBot.setItemSlot(EquipmentSlot.MAINHAND, gear(registries, Items.SHIELD,
                    new Ench(Enchantments.UNBREAKING, 3), new Ench(Enchantments.MENDING, 1)));
        } else {
            newBot.setItemSlot(EquipmentSlot.MAINHAND, gear(registries, Items.NETHERITE_SWORD,
                    new Ench(Enchantments.SHARPNESS, 5), new Ench(Enchantments.UNBREAKING, 3), new Ench(Enchantments.MENDING, 1)));
        }
        newBot.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        newBot.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        newBot.setYRot(yaw);
        // Survivability comes purely from popping real totems now (PracticeBotAi keeps the
        // offhand stocked), NOT from potion effects. That's both more realistic and more
        // useful to practise against: you see the pop, the bot drops to half a heart and
        // gets vanilla's own post-totem Regeneration/Absorption, and you have to burst it
        // down again -- exactly the loop of a real fight, instead of chipping at something
        // that silently heals or shrugs damage off.
        //
        // Resistance 255 (old approach) reduced incoming damage to a flat zero, which also
        // suppressed knockback and hit reactions -- functionally identical to the
        // setInvulnerable(true) it was meant to replace, and the reason hits felt like they
        // weren't landing.
        //
        // All three are explicitly STRIPPED rather than just not re-applied: FakePlayer.get()
        // returns a CACHED instance per (level, profile), so a bot summoned by an older
        // build is still carrying Resistance/Regeneration and would otherwise keep them
        // through this change.
        newBot.removeEffect(MobEffects.RESISTANCE);
        newBot.removeEffect(MobEffects.REGENERATION);
        newBot.setInvulnerable(false);
        newBot.setHealth(newBot.getMaxHealth());

        // Cancels the knockback resistance a full netherite set grants (0.1 per piece = 0.4),
        // so hits knock the bot back like they would an unarmoured player. It keeps
        // netherite's damage reduction -- only the knockback immunity goes.
        //
        // MUST be ADD_MULTIPLIED_TOTAL, not ADD_VALUE. An earlier attempt used
        // ADD_VALUE(-1.0) assuming the RangedAttribute would clamp the result to its [0,1]
        // range; it does NOT -- diagnostics showed the live value sitting at -1.00. Vanilla
        // then computes `strength *= 1.0 - resistance`, i.e. `strength *= 2.0`, so that
        // "fix" DOUBLED every knockback instead of removing the resistance, which is what
        // was launching the bot across the field. ADD_MULTIPLIED_TOTAL(-1.0) scales the
        // total by (1 + -1) = exactly 0 and cannot go negative no matter what is worn.
        AttributeInstance kbResist = newBot.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbResist != null) {
            kbResist.removeModifier(NO_KNOCKBACK_RESISTANCE); // re-summon reuses a CACHED FakePlayer
            // Cancels the armour's knockback resistance so hits land like they would on an
            // unarmoured player. ADD_MULTIPLIED_TOTAL(-1.0) scales the total to exactly 0;
            // it must NOT be ADD_VALUE(-1.0), which leaves it at -1.0 and DOUBLES knockback
            // (vanilla computes `strength *= 1.0 - resistance`).
            if (requested == PracticeBotAi.Mode.UNMOVEABLE) {
                // Full immunity: vanilla computes `strength *= 1.0 - resistance`, so a resistance
                // of >= 1 makes strength <= 0 and knockback() returns before touching velocity.
                kbResist.addPermanentModifier(new AttributeModifier(
                        NO_KNOCKBACK_RESISTANCE, 1.0, AttributeModifier.Operation.ADD_VALUE));
            } else {
                kbResist.addPermanentModifier(new AttributeModifier(
                        NO_KNOCKBACK_RESISTANCE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        newBot.setCustomName(Component.literal(real.name() + "'s Practice Bot"));
        newBot.setCustomNameVisible(true);

        // Always re-send the player-info: a client with no tab-list entry for this UUID
        // rejects the spawn packet outright ("Server attempted to add player prior to
        // sending player info") and the bot simply never appears.
        server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(newBot)));

        // Add the entity ONCE per level, never re-add. A previous build discarded and re-added
        // an already-tracked bot to force the tracker to re-pair it, and that left a GHOST
        // COPY on the client: a recording showed two name tags at different distances plus a
        // stray hitbox sitting on the ground. The ghost is stale, so the server never moves
        // it -- you end up watching a motionless duplicate while the real bot is knocked back
        // invisibly, which is exactly why knockback appeared broken in every mode.
        //
        // That hack existed to work around clients rejecting the spawn packet, and its cause
        // is gone: BotTrackingMixin now sends the player-info immediately before every spawn
        // packet, so the client accepts the entity first time and no re-pairing is needed.
        // Re-summoning just repositions the cached instance (setPos above) and vanilla's
        // tracker syncs the move normally.
        if (!alreadyTracked) {
            level.addFreshEntity(newBot);
        }
        bot = newBot;
        mode = requested;
        PracticeBotAi.reset(); // don't carry velocity state across a mode switch
        onSuccess.run();
    }

    private static int remove(CommandContext<FabricClientCommandSource> ctx) {
        FabricClientCommandSource source = ctx.getSource();
        Minecraft mc = source.getClient();
        MinecraftServer server = mc.getSingleplayerServer();
        if (bot == null || server == null) {
            bot = null;
            source.sendFeedback(Component.literal("No practice bot to remove."));
            return 0;
        }
        server.execute(() -> {
            if (bot != null && bot.isAlive()) {
                bot.discard();
            }
            bot = null;
            mode = PracticeBotAi.Mode.IDLE;
            PracticeBotAi.reset();
            PracticeBotState.get().active = false;
            PracticeBotState.save();
            mc.execute(() -> source.sendFeedback(Component.literal("Practice bot removed.")));
        });
        return 1;
    }

    private static ItemStack armor(RegistryAccess registries, Item item) {
        return gear(registries, item,
                new Ench(Enchantments.PROTECTION, 4), new Ench(Enchantments.UNBREAKING, 3), new Ench(Enchantments.MENDING, 1));
    }

    private record Ench(ResourceKey<Enchantment> key, int level) {
    }

    private static ItemStack gear(RegistryAccess registries, Item item, Ench... enchants) {
        ItemStack stack = new ItemStack(item);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        Registry<Enchantment> registry = registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (Ench e : enchants) {
            Holder<Enchantment> holder = registry.get(e.key().identifier()).orElseThrow();
            mutable.set(holder, e.level());
        }
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }
}
