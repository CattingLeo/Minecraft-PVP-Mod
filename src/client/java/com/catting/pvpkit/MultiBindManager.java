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
 * Fires multi-bind slots in list order, so several actions can share one key
 * and run top-to-bottom predictably.
 *
 * Two details that matter:
 *
 * 1. Slots are evaluated in index order every tick, so slot 1 always resolves
 *    before slot 2. That's the whole point -- vanilla fires every KeyMapping
 *    sharing a key, but in registration order, which no one controls.
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

    private static final boolean[] wasDown = new boolean[MultiBindConfig.SLOT_COUNT];
    private static final int[] releaseCountdown = new int[MultiBindConfig.SLOT_COUNT];
    private static final KeyMapping[] held = new KeyMapping[MultiBindConfig.SLOT_COUNT];

    private MultiBindManager() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MultiBindManager::onTick);
    }

    private static void onTick(Minecraft mc) {
        // Release anything held from a previous tick first, so a held vanilla key
        // never sticks on if the slot is reconfigured mid-hold.
        for (int i = 0; i < MultiBindConfig.SLOT_COUNT; i++) {
            if (releaseCountdown[i] > 0 && --releaseCountdown[i] == 0 && held[i] != null) {
                held[i].setDown(false);
                held[i] = null;
            }
        }

        if (mc.player == null || !mc.mouseHandler.isMouseGrabbed()) return;

        var slots = MultiBindConfig.get().slots;
        for (int i = 0; i < slots.size() && i < MultiBindConfig.SLOT_COUNT; i++) {
            Slot slot = slots.get(i);
            if (slot.action == MultiAction.NONE) continue;
            InputConstants.Key key = InputConstants.getKey(slot.key);
            if (key == null || key == InputConstants.UNKNOWN) continue;

            boolean down = isPhysicallyDown(mc, key);
            if (down && !wasDown[i]) {
                run(mc, slot.action, i);
            }
            wasDown[i] = down;
        }
    }

    private static void run(Minecraft mc, MultiAction action, int index) {
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
            case ATTACK -> hold(mc.options.keyAttack, index);
            case USE -> hold(mc.options.keyUse, index);
            case SWAP_OFFHAND -> hold(mc.options.keySwapOffhand, index);
            case DROP -> hold(mc.options.keyDrop, index);
            case JUMP -> hold(mc.options.keyJump, index);
            case SNEAK -> hold(mc.options.keyShift, index);
            case SPRINT -> hold(mc.options.keySprint, index);
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
    private static void hold(KeyMapping mapping, int index) {
        if (mapping == null) return;
        mapping.setDown(true);
        held[index] = mapping;
        releaseCountdown[index] = 3;
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
