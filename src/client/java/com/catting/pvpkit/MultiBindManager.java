package com.catting.pvpkit;

import com.catting.nocooldown.NoCooldownConfig;
import com.catting.pvpkit.MultiBindConfig.MultiAction;
import com.catting.pvpkit.MultiBindConfig.Slot;
import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;

/**
 * Fires multi-bind slots in the order each one's key was bound (Slot#addedSeq),
 * so several actions can share one key and run predictably regardless of which
 * order they happen to sit in config/multibind.json.
 *
 * Two details that matter:
 *
 * 1. Bindings are sorted by addedSeq every tick, not by list position -- that's
 *    the whole point -- vanilla fires every KeyMapping sharing a key, but in
 *    registration order, which no one controls.
 *
 * 2. Vanilla actions are driven by holding the REAL KeyMapping down via the
 *    public `setDown(true)`, not by faking clicks. Going through the actual
 *    mapping means the game's own input handling does the work, so attack
 *    cooldowns, use-item timing and sprint all behave exactly as if the key
 *    were physically held -- no reimplementation, and nothing to drift out of
 *    sync with vanilla. They're released a couple of ticks later since these
 *    are momentary triggers. Hotbar slots are the exception: setting the slot
 *    directly (plus the sync packet) is more reliable than holding nine
 *    separate hotbar keymappings.
 *
 * Key state is polled from GLFW rather than read off KeyMapping#isDown,
 * because a slot's key usually ISN'T bound to any real keymapping -- that's
 * the point of binding multiple actions to it here.
 */
public final class MultiBindManager {

    // Keyed by the Slot object's identity (Gson-loaded objects are stable in memory for
    // the process lifetime), since the binding list is no longer a fixed-size, fixed-index
    // array of rows -- any action can have any number of bound keys.
    private static final java.util.Map<Slot, Boolean> wasDown = new java.util.IdentityHashMap<>();
    private static final java.util.Map<Slot, Integer> releaseCountdown = new java.util.IdentityHashMap<>();
    private static final java.util.Map<Slot, KeyMapping> held = new java.util.IdentityHashMap<>();

    private MultiBindManager() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MultiBindManager::onTick);
    }

    private static void onTick(Minecraft mc) {
        // Release anything held from a previous tick first, so a held vanilla key
        // never sticks on if the slot is reconfigured mid-hold.
        for (var it = releaseCountdown.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            int left = entry.getValue() - 1;
            if (left <= 0) {
                KeyMapping mapping = held.remove(entry.getKey());
                if (mapping != null) mapping.setDown(false);
                it.remove();
            } else {
                entry.setValue(left);
            }
        }

        if (mc.player == null || !mc.mouseHandler.isMouseGrabbed()) return;

        // Fire in the order the bindings were ADDED, not list order -- a binding added
        // later always runs after one added earlier, even if it appears above it.
        var raw = MultiBindConfig.get().slots;
        var order = new java.util.ArrayList<>(raw);
        order.removeIf(s -> s.action == MultiAction.NONE);
        order.sort(java.util.Comparator.comparingInt(s -> s.addedSeq));

        for (Slot slot : order) {
            InputConstants.Key key = InputConstants.getKey(slot.key);
            if (key == null || key == InputConstants.UNKNOWN) continue;

            boolean down = isPhysicallyDown(mc, key);
            if (down && !wasDown.getOrDefault(slot, false)) {
                run(mc, slot.action, slot);
            }
            wasDown.put(slot, down);
        }
    }

    private static void run(Minecraft mc, MultiAction action, Slot slot) {
        switch (action) {
            case HOTBAR_1 -> hotbar(mc, 0);
            case HOTBAR_2 -> hotbar(mc, 1);
            case HOTBAR_3 -> hotbar(mc, 2);
            case HOTBAR_4 -> hotbar(mc, 3);
            case HOTBAR_5 -> hotbar(mc, 4);
            case HOTBAR_6 -> hotbar(mc, 5);
            case HOTBAR_7 -> hotbar(mc, 6);
            case HOTBAR_8 -> hotbar(mc, 7);
            case HOTBAR_9 -> hotbar(mc, 8);
            case ATTACK -> hold(mc.options.keyAttack, slot);
            case USE -> hold(mc.options.keyUse, slot);
            case SWAP_OFFHAND -> hold(mc.options.keySwapOffhand, slot);
            case DROP -> hold(mc.options.keyDrop, slot);
            case JUMP -> hold(mc.options.keyJump, slot);
            case SNEAK -> hold(mc.options.keyShift, slot);
            case SPRINT -> hold(mc.options.keySprint, slot);
            case TOGGLE_FULLBRIGHT -> ScrollActions.run(PvpKitConfig.ScrollAction.FULLBRIGHT);
            case TOGGLE_FREECAM -> FreecamManager.toggle();
            case TOGGLE_KILL_AURA -> {
                NoCooldownConfig c = NoCooldownConfig.get();
                c.killAura = !c.killAura;
                NoCooldownConfig.save();
            }
            case TOGGLE_FLIGHT -> {
                NoCooldownConfig c = NoCooldownConfig.get();
                c.flightEnabled = !c.flightEnabled;
                NoCooldownConfig.save();
            }
            default -> {
            }
        }
    }

    /** Holds a real vanilla KeyMapping down briefly, letting the game's own input handling drive the action. */
    private static void hold(KeyMapping mapping, Slot slot) {
        if (mapping == null) return;
        mapping.setDown(true);
        held.put(slot, mapping);
        releaseCountdown.put(slot, 3);
    }

    private static void hotbar(Minecraft mc, int slot) {
        Inventory inv = mc.player.getInventory();
        inv.setSelectedSlot(slot);
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private static boolean isPhysicallyDown(Minecraft mc, InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }
}
