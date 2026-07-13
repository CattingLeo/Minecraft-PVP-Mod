package com.catting.nocooldown;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

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
        if (food.getSaturationLevel() < 20.0f) food.setSaturationLevel(20.0f);
    }
}
