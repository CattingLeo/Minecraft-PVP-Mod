package com.catting.pvpkit;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.catting.pvpkit.MacroConfig.Step;
import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Runs the two macros built from the "+" rows on the Key Binds screen. A macro is an
 * ordered list of vanilla keybinds; each step is fired by holding the REAL KeyMapping
 * down briefly -- the same technique MultiBindManager uses -- so attack cooldowns,
 * use-item timing and sprint all behave exactly as if the key were physically held,
 * with nothing reimplemented client-side to drift out of sync with vanilla.
 *
 * Each macro is triggered by pressing any key bound to one of its own steps (that's
 * what the per-duplicate key binding is for), so a macro needs no separate global
 * keybind of its own.
 *
 * One Tick: every step fires the same tick.
 * Step by Step: one step fires per tick, in list order, spread across ticks.
 */
public final class MacroManager {

    private static final Map<KeyMapping, Integer> releaseCountdown = new IdentityHashMap<>();
    private static List<KeyMapping> stepQueue = null;
    private static int stepIndex = 0;
    private static boolean oneTickTriggerWasDown = false;
    private static boolean stepTriggerWasDown = false;

    private MacroManager() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MacroManager::onTick);
    }

    private static void onTick(Minecraft mc) {
        for (var it = releaseCountdown.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            int left = entry.getValue() - 1;
            if (left <= 0) {
                entry.getKey().setDown(false);
                it.remove();
            } else {
                entry.setValue(left);
            }
        }

        // Advance a running Step-by-Step macro: one action per tick.
        if (stepQueue != null) {
            if (stepIndex < stepQueue.size()) {
                fire(stepQueue.get(stepIndex));
                stepIndex++;
            } else {
                stepQueue = null;
            }
        }

        if (mc.player == null || !mc.mouseHandler.isMouseGrabbed()) return;

        MacroConfig cfg = MacroConfig.get();
        boolean oneTickDown = anyTriggerDown(mc, cfg.oneTick);
        if (oneTickDown && !oneTickTriggerWasDown) runOneTick(mc);
        oneTickTriggerWasDown = oneTickDown;

        boolean stepDown = anyTriggerDown(mc, cfg.stepByStep);
        if (stepDown && !stepTriggerWasDown) runStepByStep(mc);
        stepTriggerWasDown = stepDown;
    }

    /**
     * True while any key bound to one of this macro's steps is physically held. Polled
     * from GLFW rather than KeyMapping#isDown for the same reason MultiBindManager does
     * it: these keys usually aren't attached to any real keymapping of their own.
     */
    private static boolean anyTriggerDown(Minecraft mc, List<Step> steps) {
        for (Step s : steps) {
            InputConstants.Key key = InputConstants.getKey(s.boundKey);
            if (key == null || key == InputConstants.UNKNOWN) continue;
            if (key.getType() == InputConstants.Type.MOUSE) {
                if (GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS) return true;
            } else if (InputConstants.isKeyDown(mc.getWindow(), key.getValue())) {
                return true;
            }
        }
        return false;
    }

    public static void runOneTick(Minecraft mc) {
        if (mc.player == null) return;
        for (KeyMapping m : resolve(MacroConfig.get().oneTick)) fire(m);
    }

    public static void runStepByStep(Minecraft mc) {
        if (mc.player == null) return;
        List<KeyMapping> resolved = resolve(MacroConfig.get().stepByStep);
        if (resolved.isEmpty()) return;
        stepQueue = resolved;
        stepIndex = 0;
    }

    private static List<KeyMapping> resolve(List<Step> steps) {
        List<KeyMapping> out = new ArrayList<>();
        for (Step s : steps) {
            KeyMapping m = KeyMapping.get(s.keyName);
            if (m != null) out.add(m);
        }
        return out;
    }

    private static void fire(KeyMapping m) {
        m.setDown(true);
        releaseCountdown.put(m, 3);
    }
}
