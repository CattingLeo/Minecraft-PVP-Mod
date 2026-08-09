package com.catting.pvpkit;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Tracks which duplicate row (if any) is currently waiting for the player to press a
 * key, so KeyBindsScreenMixin can route the next keypress / mouse button / scroll notch
 * to it. Mirrors how vanilla's own Key Binds screen works -- it keeps a `selectedKey`
 * field on the screen and consumes the next input -- rather than trying to read raw
 * input from inside a Button, which never receives keyboard events.
 */
public final class MacroKeyCapture {

    private static MacroRowEntry capturing;

    private MacroKeyCapture() {
    }

    public static void begin(MacroRowEntry row) {
        capturing = row;
        row.refreshEntry();
    }

    public static boolean isCapturing(MacroRowEntry row) {
        return capturing == row;
    }

    public static boolean active() {
        return capturing != null;
    }

    /** Binds the pressed key to the waiting row and stops capturing. Returns true if a capture was consumed. */
    public static boolean accept(InputConstants.Key key) {
        if (capturing == null) return false;
        MacroRowEntry row = capturing;
        capturing = null;
        row.bind(key);
        return true;
    }

    public static void cancel() {
        if (capturing == null) return;
        MacroRowEntry row = capturing;
        capturing = null;
        row.refreshEntry();
    }
}
