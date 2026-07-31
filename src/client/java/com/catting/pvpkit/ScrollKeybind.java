package com.catting.pvpkit;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;

/**
 * Makes the scroll wheel assignable as a real keybind: open Key Binds, click any
 * action, scroll, and the wheel is bound to it. Scrolling in-game then fires it.
 *
 * How it works: Minecraft has no "scroll" input type, only KEYSYM/SCANCODE/MOUSE.
 * So we mint a synthetic MOUSE button at an index GLFW never produces (GLFW tops
 * out at button 8 / index 7, so 10 is safe) and treat it as "the scroll wheel".
 * `ScrollHotbarMixin` assigns that key when you scroll with a keybind selected,
 * and fires it when you scroll during play. Because it's an ordinary
 * InputConstants.Key, everything downstream -- vanilla keybinds, this mod's, and
 * other mods' -- handles it with no special-casing, and it saves to options.txt
 * like any other binding.
 *
 * Deliberately one key for both directions, per request ("any direction").
 *
 * The display name comes from the `key.mouse.10` entry in this mod's lang file,
 * so it reads "Scroll Wheel" in the controls screen rather than "Button 11".
 */
public final class ScrollKeybind {

    /** Index 10: past GLFW's 8-button range, so no physical button can ever collide with it. */
    public static final InputConstants.Key SCROLL_KEY = InputConstants.Type.MOUSE.getOrCreate(10);

    /** Ticks left before we release the synthetic press (scroll is momentary, so we fake a short hold). */
    private static int releaseCountdown;

    private ScrollKeybind() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (releaseCountdown > 0 && --releaseCountdown == 0) {
                KeyMapping.set(SCROLL_KEY, false);
            }
        });
    }

    /**
     * Fires the wheel as a key press. Does both `click` and `set(true)` because
     * keybinds are consumed two different ways: one-shot actions poll
     * `consumeClick()` (needs the click counter bumped) while held actions poll
     * `isDown()` (needs the pressed flag). Doing only one would silently work for
     * half of all keybinds. The press is released a couple of ticks later, since a
     * scroll notch has no natural "release" event of its own.
     */
    public static void fire() {
        KeyMapping.click(SCROLL_KEY);
        KeyMapping.set(SCROLL_KEY, true);
        releaseCountdown = 2;
    }
}
