package com.catting.nocooldown;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * All No Cooldown keybinds, grouped under their own "No Cooldown" section in
 * Options > Controls > Key Binds. Every key starts UNBOUND (GLFW_KEY_UNKNOWN)
 * -- nothing fires until you bind a key yourself.
 *
 * Every action gets TWO independent keybind slots (e.g. bind one to a mouse
 * button, the other to a keyboard key) -- each shows up as its own row in
 * Key Binds, both start unbound, and either one alone triggers the action.
 *
 * Reuses the exact KeyMapping.Category pattern already confirmed compiling
 * cleanly in PvP Kit (the only errors in that build were unrelated chat-message
 * calls sitting right next to this same infrastructure, which compiled fine).
 */
public final class NoCooldownKeybinds {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nocooldown", "no_cooldown"));

    private static final KeyMapping CYCLE_MODE = bind("cycle_mode");
    private static final KeyMapping CYCLE_MODE_2 = bind2("cycle_mode");
    private static final KeyMapping TOGGLE_DURABILITY = bind("toggle_durability");
    private static final KeyMapping TOGGLE_DURABILITY_2 = bind2("toggle_durability");
    private static final KeyMapping TOGGLE_INSTANT_USE = bind("toggle_instant_use");
    private static final KeyMapping TOGGLE_INSTANT_USE_2 = bind2("toggle_instant_use");
    private static final KeyMapping TOGGLE_NO_DAMAGE = bind("toggle_no_damage");
    private static final KeyMapping TOGGLE_NO_DAMAGE_2 = bind2("toggle_no_damage");
    private static final KeyMapping TOGGLE_FLIGHT = bind("toggle_flight");
    private static final KeyMapping TOGGLE_FLIGHT_2 = bind2("toggle_flight");
    private static final KeyMapping TOGGLE_HUNGER = bind("toggle_hunger");
    private static final KeyMapping TOGGLE_HUNGER_2 = bind2("toggle_hunger");
    private static final KeyMapping TOGGLE_KILL_AURA = bind("toggle_kill_aura");
    private static final KeyMapping TOGGLE_KILL_AURA_2 = bind2("toggle_kill_aura");

    private NoCooldownKeybinds() {
    }

    private static KeyMapping bind(String path) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.nocooldown." + path, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
    }

    /** Second independent slot for the same action -- its own row, own translation key, starts unbound. */
    private static KeyMapping bind2(String path) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.nocooldown." + path + "_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(NoCooldownKeybinds::onTick);
    }

    /** Bitwise OR (not ||) so both slots' click queues get drained every iteration, not just the first with clicks pending. */
    private static void onTick(Minecraft mc) {
        while (CYCLE_MODE.consumeClick() | CYCLE_MODE_2.consumeClick()) cycleMode();
        while (TOGGLE_DURABILITY.consumeClick() | TOGGLE_DURABILITY_2.consumeClick()) toggle(c -> c.unlimitedDurability = !c.unlimitedDurability);
        while (TOGGLE_INSTANT_USE.consumeClick() | TOGGLE_INSTANT_USE_2.consumeClick()) toggle(c -> c.instantUse = !c.instantUse);
        while (TOGGLE_NO_DAMAGE.consumeClick() | TOGGLE_NO_DAMAGE_2.consumeClick()) toggle(c -> c.noDamage = !c.noDamage);
        while (TOGGLE_FLIGHT.consumeClick() | TOGGLE_FLIGHT_2.consumeClick()) toggle(c -> c.flightEnabled = !c.flightEnabled);
        while (TOGGLE_HUNGER.consumeClick() | TOGGLE_HUNGER_2.consumeClick()) toggle(c -> c.infiniteHunger = !c.infiniteHunger);
        while (TOGGLE_KILL_AURA.consumeClick() | TOGGLE_KILL_AURA_2.consumeClick()) toggle(c -> c.killAura = !c.killAura);
    }

    private interface ConfigEdit {
        void apply(NoCooldownConfig c);
    }

    /** No cooldown's mixins read NoCooldownConfig.get() live each time they fire,
     *  so a toggle just needs to mutate + save -- no separate "apply" step. */
    private static void toggle(ConfigEdit edit) {
        NoCooldownConfig c = NoCooldownConfig.get();
        edit.apply(c);
        NoCooldownConfig.save();
    }

    private static void cycleMode() {
        NoCooldownConfig c = NoCooldownConfig.get();
        NoCooldownConfig.Mode[] values = NoCooldownConfig.Mode.values();
        c.mode = values[(c.mode.ordinal() + 1) % values.length];
        NoCooldownConfig.save();
    }
}
