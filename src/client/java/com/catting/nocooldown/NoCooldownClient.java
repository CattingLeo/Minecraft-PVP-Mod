package com.catting.nocooldown;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public class NoCooldownClient implements ClientModInitializer {

    /** Tracks whether WE granted flight, so turning the toggle off never
     *  revokes real creative/spectator flight that was already there. */
    private static boolean weGrantedFlight = false;

    @Override
    public void onInitializeClient() {
        NoCooldownConfig.load();
        NoCooldownKeybinds.register();
        ClientTickEvents.END_CLIENT_TICK.register(NoCooldownClient::updateFlight);
        ClientTickEvents.END_CLIENT_TICK.register(NoCooldownClient::updateHunger);
        ClientTickEvents.END_CLIENT_TICK.register(NoCooldownClient::updateKillAura);
    }

    /**
     * Flight reuses the exact same permission creative/spectator mode already
     * has (Abilities#mayfly), synced to the server via onUpdateAbilities() --
     * both long-standing, stable vanilla APIs, no mixin needed. Only ever turns
     * mayfly ON when the toggle is on, and only turns it back OFF if we're the
     * one who granted it and the player isn't genuinely creative/spectator.
     */
    private static void updateFlight(Minecraft mc) {
        if (mc.player == null) return;
        var abilities = mc.player.getAbilities();
        boolean vanillaFlight = mc.player.isCreative() || mc.player.isSpectator();

        if (NoCooldownConfig.get().flightEnabled) {
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                weGrantedFlight = true;
                mc.player.onUpdateAbilities();
            }
        } else if (weGrantedFlight && !vanillaFlight) {
            abilities.mayfly = false;
            abilities.flying = false;
            weGrantedFlight = false;
            mc.player.onUpdateAbilities();
        }
    }

    /**
     * Keeps food/saturation pinned at full while enabled. HungerMixin stops the
     * bar from draining; this makes it visually full immediately too, rather
     * than just frozen wherever it was when you flipped the toggle.
     */
    private static void updateHunger(Minecraft mc) {
        if (mc.player == null || !NoCooldownConfig.get().infiniteHunger) return;
        var food = mc.player.getFoodData();
        if (food.getFoodLevel() < 20) food.setFoodLevel(20);
        if (food.getSaturationLevel() < 20.0f) food.setSaturation(20.0f);
    }

    /**
     * Kill Aura: attacks the nearest valid target in range, but only when the
     * attack meter is fully charged (so every swing is a full-strength vanilla
     * hit -- no packet spam) and only with genuine line of sight (never through
     * walls). Uses the same MultiPlayerGameMode#attack path a real click takes.
     *
     * NOTE: unlike the other sandbox toggles on this page, attacking is
     * client-initiated, so this one DOES work on servers you don't host --
     * which is exactly why it must stay a private-world / LAN-with-friends
     * toggle. See the Fair use section in README.md.
     */
    private static void updateKillAura(Minecraft mc) {
        NoCooldownConfig c = NoCooldownConfig.get();
        if (!c.killAura || mc.player == null || mc.level == null || mc.gameMode == null) return;
        // Mouse grabbed == actively playing with no GUI/chat open (and window
        // focused) -- Minecraft no longer exposes the current screen directly.
        if (!mc.mouseHandler.isMouseGrabbed() || mc.player.isSpectator() || !mc.player.isAlive()) return;
        if (mc.player.getAttackStrengthScale(0.0f) < 1.0f) return;

        double range = c.killAuraRange;
        LivingEntity target = null;
        double best = range * range;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(range), e -> isKillAuraTarget(mc, e))) {
            double d = mc.player.distanceToSqr(e);
            if (d <= best) {
                best = d;
                target = e;
            }
        }
        if (target != null) {
            mc.gameMode.attack(mc.player, target);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static boolean isKillAuraTarget(Minecraft mc, LivingEntity e) {
        if (e == mc.player || !e.isAlive()) return false;
        boolean typeOk = switch (NoCooldownConfig.get().killAuraTargets) {
            case PLAYERS -> e instanceof Player p && !p.isSpectator();
            case HOSTILE_MOBS -> e instanceof Enemy;
            case ALL_MOBS -> e instanceof Enemy || (e instanceof Mob) || (e instanceof Player p && !p.isSpectator());
        };
        return typeOk && mc.player.hasLineOfSight(e);
    }
}
