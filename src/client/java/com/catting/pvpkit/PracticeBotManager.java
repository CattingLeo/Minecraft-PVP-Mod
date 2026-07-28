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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
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
 * profile). addFreshEntity is only called when the bot isn't already
 * tracked in that level (checked via Level#getEntity(UUID)).
 *
 * A player-type entity is silently discarded client-side ("Skipping Entity
 * with id entity.minecraft.player") if the client hasn't already been told
 * this UUID's GameProfile -- normally sent by PlayerList#placeNewPlayer
 * during a real player's join, which this FakePlayer never goes through.
 * ClientboundPlayerInfoUpdatePacket.createPlayerInitializing replicates
 * that same initial-info packet so the client accepts the entity.
 *
 * Not setInvulnerable(true) -- that skips the entire vanilla hurt pipeline,
 * including knockback (applied in the same call), which made hits and wind
 * burst do nothing. Resistance 255 + Regeneration II instead: real hit
 * reactions/knockback/sounds through the normal pipeline, it just never
 * actually dies.
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

    private static FakePlayer bot;
    private static PracticeBotAi.Mode mode = PracticeBotAi.Mode.IDLE;

    private PracticeBotManager() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            var root = ClientCommands.literal("practicebot")
                    .executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.IDLE))
                    .then(ClientCommands.literal("remove").executes(PracticeBotManager::remove));
            // One subcommand per mode, named from its label ("elytra mace" ->
            // /practicebot elytra mace, i.e. nested literals).
            root = root.then(ClientCommands.literal("shield").executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.SHIELD)));
            root = root.then(ClientCommands.literal("sword").executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.SWORD)));
            root = root.then(ClientCommands.literal("axe").executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.AXE)));
            root = root.then(ClientCommands.literal("defend").executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.DEFEND)));
            root = root.then(ClientCommands.literal("crystal").executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.CRYSTAL)));
            root = root.then(ClientCommands.literal("mace")
                    .executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.MACE)));
            root = root.then(ClientCommands.literal("elytra")
                    .then(ClientCommands.literal("mace").executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.ELYTRA_MACE))));
            root = root.then(ClientCommands.literal("firework")
                    .then(ClientCommands.literal("mace").executes(ctx -> summon(ctx.getSource(), PracticeBotAi.Mode.FIREWORK_MACE))));
            dispatcher.register(root);
        });

        // Runs on the server thread already (this event fires there), so no
        // server.execute() hop needed.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (bot == null || !bot.isAlive()) return;
            ServerLevel level = (ServerLevel) bot.level();
            PracticeBotAi.tick(bot, mode, level);
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
        // The flying mace modes wear an elytra instead of the chestplate, so it's
        // visible on its back mid-dive (the glide animation itself won't play --
        // that needs a non-public gliding flag; see DEVELOPMENT.md).
        boolean flying = requested == PracticeBotAi.Mode.ELYTRA_MACE || requested == PracticeBotAi.Mode.FIREWORK_MACE;
        newBot.setItemSlot(EquipmentSlot.CHEST, flying
                ? gear(registries, Items.ELYTRA, new Ench(Enchantments.UNBREAKING, 3), new Ench(Enchantments.MENDING, 1))
                : armor(registries, Items.NETHERITE_CHESTPLATE));

        Item weapon = switch (requested) {
            case AXE -> Items.NETHERITE_AXE;
            case MACE, ELYTRA_MACE, FIREWORK_MACE -> Items.MACE;
            case CRYSTAL -> Items.END_CRYSTAL;
            default -> Items.NETHERITE_SWORD;
        };
        newBot.setItemSlot(EquipmentSlot.MAINHAND, gear(registries, weapon,
                new Ench(Enchantments.SHARPNESS, 5), new Ench(Enchantments.UNBREAKING, 3), new Ench(Enchantments.MENDING, 1)));
        newBot.setItemSlot(EquipmentSlot.OFFHAND, gear(registries, Items.SHIELD,
                new Ench(Enchantments.UNBREAKING, 3), new Ench(Enchantments.MENDING, 1)));

        newBot.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        newBot.setYRot(yaw);
        newBot.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, Integer.MAX_VALUE, 255, false, false, false));
        newBot.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Integer.MAX_VALUE, 1, false, false, false));
        newBot.setCustomName(Component.literal(real.name() + "'s Practice Bot"));
        newBot.setCustomNameVisible(true);

        if (!alreadyTracked) {
            server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(newBot)));
            level.addFreshEntity(newBot);
        }
        bot = newBot;
        mode = requested;
        PracticeBotAi.reset(); // don't inherit a half-finished mace arc across a mode switch
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
