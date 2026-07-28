package com.catting.pvpkit;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
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
 * /practicebot -- summons a full-netherite, unkillable combat dummy wearing
 * your own name and skin, standing exactly where you're standing (via
 * Fabric API's FakePlayer, a real ServerPlayer-derived entity with a
 * working stand-in network listener -- not a genuine connected player, and
 * not something this mod built from scratch: FakePlayer is Fabric API's own
 * sanctioned tool for exactly this, already a transitive dependency via
 * fabric-events-interaction-v0).
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
 * on the client/render thread the command handler fires on. The first
 * version skipped this and called ServerLevel#addFreshEntity directly from
 * the command callback; even in singleplayer the integrated server runs on
 * its own thread, and C2ME's threading-safety mixin (one of this user's
 * other installed mods) correctly threw ConcurrentModificationException
 * ("Async entity load") for the illegal cross-thread chunk/entity access --
 * the bot never actually finished being added, which is why it never
 * appeared. Client-only values needed inside the server task (profile,
 * exact spawn position/yaw) are captured as local final data before the
 * hop, and feedback messages are hopped back via mc.execute(...) rather
 * than sent directly from the server thread.
 *
 * Uses a fixed synthetic UUID (nameUUIDFromBytes, NOT your real account
 * UUID -- two entities can never share one) so repeat /practicebot calls
 * reuse and reposition the same FakePlayer instance rather than piling up
 * duplicates: Fabric caches FakePlayer.get() results keyed by (level,
 * profile). addFreshEntity is only called when the bot isn't already
 * tracked in that level (checked via Level#getEntity(UUID)) -- calling it
 * again on an already-tracked entity logs "UUID of added entity already
 * exists" and does nothing, so repositioning instead just mutates the
 * existing entity directly (normal per-tick entity sync picks up the
 * moved position on its own).
 */
public final class PracticeBotManager {

    private static final UUID BOT_UUID =
            UUID.nameUUIDFromBytes("pvpkit:practicebot".getBytes(StandardCharsets.UTF_8));

    private static FakePlayer bot;
    private static boolean shieldMode;

    private PracticeBotManager() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
                ClientCommands.literal("practicebot")
                        .executes(ctx -> summon(ctx.getSource(), false))
                        .then(ClientCommands.literal("shield").executes(ctx -> summon(ctx.getSource(), true)))
                        .then(ClientCommands.literal("remove").executes(PracticeBotManager::remove))));

        // Keeps the shield raised every server tick rather than a one-shot call,
        // since startUsingItem() only needs to fire again if something (e.g. use
        // duration lapsing) ever drops it back out of the "using item" state.
        // Already runs on the server thread (this event fires there), so no
        // server.execute() hop needed here.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (bot != null && shieldMode && bot.isAlive() && !bot.isUsingItem()) {
                bot.startUsingItem(InteractionHand.OFF_HAND);
            }
        });
    }

    private static int summon(FabricClientCommandSource source, boolean shield) {
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
        GameProfile fake = new GameProfile(BOT_UUID, real.name(), new PropertyMap(real.properties()));
        Vec3 spawnPos = player.position();
        float yaw = player.getYRot() + 180.0f;
        Component nameTag = Component.literal(real.name() + "'s Practice Bot");
        Component doneMessage = Component.literal(shield ? "Practice bot summoned -- shield up." : "Practice bot summoned.");

        server.execute(() -> {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                mc.execute(() -> source.sendError(Component.literal("Couldn't find your current dimension server-side.")));
                return;
            }

            FakePlayer newBot = FakePlayer.get(level, fake);
            boolean alreadyTracked = level.getEntity(BOT_UUID) != null;

            RegistryAccess registries = level.registryAccess();
            newBot.setItemSlot(EquipmentSlot.HEAD, armor(registries, Items.NETHERITE_HELMET));
            newBot.setItemSlot(EquipmentSlot.CHEST, armor(registries, Items.NETHERITE_CHESTPLATE));
            newBot.setItemSlot(EquipmentSlot.LEGS, armor(registries, Items.NETHERITE_LEGGINGS));
            newBot.setItemSlot(EquipmentSlot.FEET, armor(registries, Items.NETHERITE_BOOTS));
            newBot.setItemSlot(EquipmentSlot.MAINHAND, gear(registries, Items.NETHERITE_SWORD,
                    new Ench(Enchantments.SHARPNESS, 5), new Ench(Enchantments.UNBREAKING, 3), new Ench(Enchantments.MENDING, 1)));
            newBot.setItemSlot(EquipmentSlot.OFFHAND, gear(registries, Items.SHIELD,
                    new Ench(Enchantments.UNBREAKING, 3), new Ench(Enchantments.MENDING, 1)));

            newBot.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            newBot.setYRot(yaw);
            // Deliberately NOT setInvulnerable(true) -- that skips the entire vanilla
            // hurt pipeline, including knockback (applied in the same call), which is
            // why hits and wind burst did nothing. Resistance 255 + Regeneration II is
            // the classic "effectively unkillable but still a real, hittable, knocked-
            // back combat participant" combo instead -- real hit reactions/knockback/
            // sounds, just never actually dies.
            newBot.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, Integer.MAX_VALUE, 255, false, false, false));
            newBot.addEffect(new MobEffectInstance(MobEffects.REGENERATION, Integer.MAX_VALUE, 1, false, false, false));
            newBot.setCustomName(nameTag);
            newBot.setCustomNameVisible(true);

            if (!alreadyTracked) {
                // A player-type entity is silently discarded client-side ("Skipping
                // Entity with id entity.minecraft.player") if the client hasn't
                // already been told this UUID's GameProfile -- normally sent by
                // PlayerList#placeNewPlayer during a real player's join, which this
                // FakePlayer never goes through. createPlayerInitializing replicates
                // that same initial-info packet (profile, skin, gamemode, listed
                // status) so the client accepts the entity when it arrives right after.
                server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(newBot)));
                level.addFreshEntity(newBot);
            }
            bot = newBot;
            shieldMode = shield;

            mc.execute(() -> source.sendFeedback(doneMessage));
        });
        return 1;
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
            shieldMode = false;
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
