package com.catting.pvpkit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
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

    /**
     * UUID of the FakePlayer profile actually in use. Starts at BOT_UUID; performSummon
     * bumps it (see botUuid) whenever the cached instance under the current UUID turns out
     * to be a dead one -- see the isRemoved() check there for why that happens.
     */
    private static UUID currentBotUuid = BOT_UUID;
    private static int generation;

    /** Gen 0 is the stable BOT_UUID (keeps the common case deterministic); later gens are derived so each is a fresh cache key. */
    private static UUID botUuid(int gen) {
        return gen == 0 ? BOT_UUID
                : UUID.nameUUIDFromBytes(("pvpkit:practicebot:" + gen).getBytes(StandardCharsets.UTF_8));
    }

    private PracticeBotManager() {
    }

    /** True only for the one FakePlayer this mod owns -- see FakePlayerTickMixin for why the scope matters. */
    public static boolean isPracticeBot(Object entity) {
        return bot != null && bot == entity;
    }

    /**
     * Velocity the bot's absent client would have been told to apply by the last hit, waiting
     * to be applied at the head of its next tick. See KnockbackReconciliationMixin.
     *
     * Server thread only (set during packet handling, read during the entity tick, both on
     * that thread).
     */
    private static Vec3 pendingKnockback;

    /** KnockbackReconciliationMixin hands over the velocity vanilla's motion packet carried. */
    public static void recordKnockback(Vec3 velocity) {
        pendingKnockback = velocity;
    }

    /** The pending hit velocity, or null. Consumed -- calling it twice returns null the second time. */
    public static Vec3 consumeKnockback() {
        Vec3 velocity = pendingKnockback;
        pendingKnockback = null;
        return velocity;
    }

    /**
     * Which protection enchant the bot's netherite set carries, so you can practise against the
     * kit that actually matters: Blast Protection for crystal/anchor trades, Projectile
     * Protection for bow and crossbow fights, and so on.
     *
     * All four cap at IV in vanilla, so the level doesn't vary per type.
     */
    public enum Protection {
        PROTECTION("protection", "Protection", Enchantments.PROTECTION),
        BLAST("blast_protection", "Blast Protection", Enchantments.BLAST_PROTECTION),
        PROJECTILE("projectile_protection", "Projectile Protection", Enchantments.PROJECTILE_PROTECTION),
        FIRE("fire_protection", "Fire Protection", Enchantments.FIRE_PROTECTION);

        /**
         * Literal typed after the mode, e.g. "/practicebot defend blast_protection". Matches the
         * vanilla enchantment id rather than a short alias, so it reads the same as it does
         * everywhere else in the game.
         *
         * Kept separate from name(): that's what gets written to practicebot.json, so renaming
         * what you type here never invalidates a saved bot.
         */
        public final String command;
        public final String label;
        public final ResourceKey<Enchantment> enchantment;

        Protection(String command, String label, ResourceKey<Enchantment> enchantment) {
            this.command = command;
            this.label = label;
            this.enchantment = enchantment;
        }
    }

    /** "<mode>", plus a "<mode> <protection>" child per armour choice. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> modeNode(PracticeBotAi.Mode mode) {
        var node = ClientCommands.literal(mode.label)
                .executes(ctx -> summon(ctx.getSource(), mode, Protection.PROTECTION));
        for (Protection protection : Protection.values()) {
            node = node.then(ClientCommands.literal(protection.command)
                    .executes(ctx -> summon(ctx.getSource(), mode, protection)));
        }
        return node;
    }

    /** Name tag without the health suffix, captured at summon. */
    private static String baseName = "Practice Bot";
    private static int shownHealth = Integer.MIN_VALUE;
    private static int shownAbsorption = Integer.MIN_VALUE;

    /**
     * Health readout above the bot, the way a PvP server shows it under a player's name.
     *
     * Done as the custom NAME rather than a rendered bar: the name tag is already synced to
     * every client that can see the entity, so this works for anyone on your LAN world with no
     * client-side render mixin and nothing for Sodium to conflict with.
     *
     * Only rebuilt when the DISPLAYED numbers change, not every tick -- setCustomName writes to
     * synched entity data, so a per-tick rebuild would be a packet every tick for a value that
     * changes a few times per fight.
     */
    public static void refreshHealthTag(FakePlayer target) {
        int health = Mth.ceil(target.getHealth());
        int absorption = Mth.ceil(target.getAbsorptionAmount());
        if (health == shownHealth && absorption == shownAbsorption) return;
        shownHealth = health;
        shownAbsorption = absorption;

        float max = target.getMaxHealth();
        float fraction = max <= 0.0f ? 0.0f : health / max;
        ChatFormatting colour = fraction > 0.6f ? ChatFormatting.GREEN
                : fraction > 0.3f ? ChatFormatting.YELLOW
                : ChatFormatting.RED;

        MutableComponent name = Component.literal(baseName + " ").withStyle(ChatFormatting.WHITE);
        name.append(Component.literal(String.valueOf(health)).withStyle(colour));
        // Absorption is what a totem pop leaves behind, so it's worth showing separately rather
        // than folded into the health number -- it's the shield you have to chew through again.
        if (absorption > 0) {
            name.append(Component.literal("+" + absorption).withStyle(ChatFormatting.GOLD));
        }
        // U+2764. Safe in the default font: its provider list falls back to
        // minecraft:include/unifont (checked in assets/minecraft/font/default.json), which
        // covers the whole BMP.
        name.append(Component.literal("❤").withStyle(colour));
        target.setCustomName(name);
    }

    /**
     * Drops the bot into an arena pod for /arena start.
     *
     * Also updates the saved spawn position, so the round-reset respawn puts it back in its pod
     * rather than wherever it was originally summoned across the world.
     */
    public static void moveToArena(net.minecraft.core.BlockPos pod) {
        if (bot == null || !bot.isAlive()) return;
        bot.teleportTo(pod.getX() + 0.5, pod.getY(), pod.getZ() + 0.5);

        PracticeBotState state = PracticeBotState.get();
        state.x = pod.getX() + 0.5;
        state.y = pod.getY();
        state.z = pod.getZ() + 0.5;
        PracticeBotState.save();
    }

    /** A tick path threw for the bot; log once rather than killing the server tick loop. */
    public static void onTickError(Exception e) {
        if (tickErrorLogged) return;
        tickErrorLogged = true;
        LOGGER.warn("[pvpkit] practice bot tick threw (suppressed, logged once)", e);
    }

    private static boolean tickErrorLogged;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("pvpkit");

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            // No .executes on the root on purpose: bare "/practicebot" is not a command, so a
            // mode always has to be named. "/practicebot idle" is the old bare behaviour.
            var root = ClientCommands.literal("practicebot")
                    .then(ClientCommands.literal("remove").executes(PracticeBotManager::remove));
            // Armour hangs off the MODES only -- never off the root. Offering
            // "/practicebot blast_protection" as well would put eight suggestions on the first
            // level and bury the four that actually pick behaviour. "/practicebot idle
            // <armour>" covers the no-special-behaviour case instead.
            for (PracticeBotAi.Mode mode : PracticeBotAi.Mode.values()) {
                root = root.then(modeNode(mode));
            }
            dispatcher.register(root);
        });

        // Runs on the server thread already (this event fires there), so no
        // server.execute() hop needed.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            announceRestoreOncePlayerExists(server);
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

    private static int summon(FabricClientCommandSource source, PracticeBotAi.Mode requested, Protection protection) {
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
        Component doneMessage = Component.literal("Practice bot summoned -- mode: " + requested.label
                + ", armour: " + protection.label + " IV.");

        server.execute(() -> performSummon(server, dimension, real, spawnPos, yaw, requested, protection,
                () -> {
                    PracticeBotState state = PracticeBotState.get();
                    state.active = true;
                    state.mode = requested.name();
                    state.protection = protection.name();
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

    /**
     * Mode restored at world load, waiting to be announced.
     *
     * The restore itself fires at SERVER_STARTED, which is BEFORE the client player exists,
     * so a chat message sent from there goes nowhere. This holds it until someone is actually
     * around to read it.
     */
    private static PracticeBotAi.Mode pendingRestoreNotice;

    /**
     * Tells you which mode the auto-restored bot came back in.
     *
     * Worth the noise: the restore used to be entirely silent, and a bot restored in
     * `unmoveable` (immune to knockback and pinned in place BY DESIGN) is indistinguishable
     * from a broken bot -- it reads as "my hits do nothing", which cost a real debugging
     * session chasing a knockback bug that wasn't there.
     */
    private static void announceRestoreOncePlayerExists(MinecraftServer server) {
        if (pendingRestoreNotice == null || server.getPlayerList().getPlayers().isEmpty()) return;
        PracticeBotAi.Mode notice = pendingRestoreNotice;
        pendingRestoreNotice = null;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            // LocalPlayer#sendSystemMessage, verified present via javap on this exact 26.2
            // build -- NOT Player#displayClientMessage or Gui#getChat(), both of which are
            // absent here (see PvpKitKeybinds#chat for the same trap).
            mc.player.sendSystemMessage(Component.literal(
                    "Practice bot restored -- mode: " + notice.label
                    + (notice == PracticeBotAi.Mode.UNMOVEABLE ? " (immune to knockback by design)" : "")
                    + ". Use /practicebot for a normal one."));
        });
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
        Protection savedProtection;
        try {
            savedProtection = Protection.valueOf(state.protection);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Unknown/renamed value, or a state file written before armour was selectable.
            savedProtection = Protection.PROTECTION;
        }
        Protection restoredProtection = savedProtection;
        float yaw = state.yaw;

        server.execute(() -> performSummon(server, dimension, real, spawnPos, yaw, restored, restoredProtection,
                () -> pendingRestoreNotice = restored,
                error -> { /* nothing to report an error to here either */ }));
    }

    /** Actual entity creation/equip/positioning, shared by the command path and the world-load restore path. Must run inside server.execute(...). */
    private static void performSummon(MinecraftServer server, ResourceKey<Level> dimension, GameProfile real,
                                       Vec3 spawnPos, float yaw, PracticeBotAi.Mode requested, Protection protection,
                                       Runnable onSuccess, Consumer<Component> onError) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            onError.accept(Component.literal("Couldn't find that dimension server-side."));
            return;
        }

        GameProfile fake = new GameProfile(currentBotUuid, real.name(), new PropertyMap(real.properties()));
        FakePlayer newBot = FakePlayer.get(level, fake);
        if (newBot.isRemoved()) {
            // Fabric API's FakePlayer.get() caches instances forever in a weakValues map
            // keyed by (level, profile) as long as something -- our own `bot` field -- holds
            // a strong reference. /practicebot remove (or the bot dying for real) discards
            // the entity, permanently setting its removed flag; there is no vanilla way to
            // undo that. The NEXT summon's get() call was handing back that same dead
            // instance, and vanilla refuses to re-add an already-removed entity ("Tried to
            // add entity minecraft:player but it was marked as removed already", confirmed
            // in a real play session's log every time this path was hit) -- so the bot
            // silently never reappeared. A new UUID is a new cache key, forcing get() to
            // construct a genuinely new FakePlayer instead.
            currentBotUuid = botUuid(++generation);
            fake = new GameProfile(currentBotUuid, real.name(), new PropertyMap(real.properties()));
            newBot = FakePlayer.get(level, fake);
        }
        boolean alreadyTracked = level.getEntity(currentBotUuid) != null;

        RegistryAccess registries = level.registryAccess();
        equipDummyKit(newBot, registries, requested, protection);

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

        // Knockback resistance is left at whatever the armour naturally grants -- for the full
        // netherite set that's 0.4 (0.1 per piece, verified in ArmorMaterials.NETHERITE's
        // bytecode), exactly what a real player in the same kit has.
        //
        // Earlier builds force-cancelled it to 0 so the bot "flew like an unarmoured player".
        // That was compensation for knockback appearing not to work AT ALL, and the real cause
        // of that has since been found and fixed properly (see KnockbackReconciliationMixin:
        // vanilla was resetting the bot's velocity straight back to its pre-hit value because
        // it assumes a ServerPlayer target has a real client to simulate the motion instead).
        // With genuine knockback restored, the old cancel stacked on top and sent the bot
        // flying ~1.67x too far -- vanilla computes `strength *= 1.0 - resistance`, so 0.0
        // resistance takes 100% of the hit where a real netherite player takes 60%.
        AttributeInstance kbResist = newBot.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbResist != null) {
            // Always strip first: FakePlayer.get() hands back a CACHED instance, so a bot
            // summoned by an older build still carries that build's modifier.
            kbResist.removeModifier(NO_KNOCKBACK_RESISTANCE);
            if (requested == PracticeBotAi.Mode.UNMOVEABLE) {
                // Full immunity: vanilla computes `strength *= 1.0 - resistance`, so a resistance
                // of >= 1 makes strength <= 0 and knockback() returns before touching velocity.
                kbResist.addPermanentModifier(new AttributeModifier(
                        NO_KNOCKBACK_RESISTANCE, 1.0, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        baseName = real.name() + "'s Practice Bot";
        shownHealth = Integer.MIN_VALUE; // force the tag to rebuild for the new bot
        shownAbsorption = Integer.MIN_VALUE;
        refreshHealthTag(newBot);
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
        pendingKnockback = null; // a hit landed on the previous bot must not shove the new one
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
            pendingKnockback = null;
            PracticeBotAi.reset();
            PracticeBotState.get().active = false;
            PracticeBotState.save();
            mc.execute(() -> source.sendFeedback(Component.literal("Practice bot removed.")));
        });
        return 1;
    }

    /**
     * The original dummy kit: full enchanted netherite plus a totem, for the modes that stand
     * there and take it.
     */
    private static void equipDummyKit(FakePlayer newBot, RegistryAccess registries,
                                      PracticeBotAi.Mode requested, Protection protection) {
        newBot.setItemSlot(EquipmentSlot.HEAD, armor(registries, Items.NETHERITE_HELMET, protection));
        newBot.setItemSlot(EquipmentSlot.LEGS, armor(registries, Items.NETHERITE_LEGGINGS, protection));
        newBot.setItemSlot(EquipmentSlot.FEET, armor(registries, Items.NETHERITE_BOOTS, protection));
        newBot.setItemSlot(EquipmentSlot.CHEST, armor(registries, Items.NETHERITE_CHESTPLATE, protection));
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
    }

    private static ItemStack armor(RegistryAccess registries, Item item, Protection protection) {
        return gear(registries, item,
                new Ench(protection.enchantment, 4), new Ench(Enchantments.UNBREAKING, 3), new Ench(Enchantments.MENDING, 1));
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
        // Truly unbreakable, not just Unbreaking III: the bot's kit takes damage every single
        // exchange and is never repaired by anything (Mending needs XP orbs, which a FakePlayer
        // never collects), so armour WOULD eventually break mid-session and quietly change what
        // you're practising against. In 26.2 this component is typed DataComponentType<Unit> --
        // not the older record with a showInTooltip flag -- verified via javap.
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        return stack;
    }
}
