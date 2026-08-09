package com.catting.pvpkit.mixin;

import com.catting.pvpkit.MacroKeyCapture;
import com.catting.pvpkit.PvpKitClient;
import com.catting.pvpkit.PvpKitConfig;
import com.catting.pvpkit.ScrollActions;
import com.catting.pvpkit.ScrollKeybind;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frees the scroll wheel from hotbar switching.
 *
 * Deliberately a @Redirect on the ONE `Inventory#setSelectedSlot` call inside
 * `MouseHandler#onScroll`, rather than cancelling onScroll wholesale: scroll is
 * also what drives GUI/creative-inventory/chat scrolling, all of which lives in
 * the same method, so a HEAD cancel would break scrolling everywhere. This
 * suppresses only the in-world hotbar change and leaves every other use intact.
 *
 * Number keys and the Fullset/other slot-selection paths are untouched, since
 * they don't route through onScroll.
 */
@Mixin(MouseHandler.class)
public class ScrollHotbarMixin {

    @Redirect(
            method = "onScroll",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void pvpkit$noScrollHotbar(Inventory inventory, int slot) {
        if (!PvpKitClient.DISABLE_SCROLL_HOTBAR) {
            inventory.setSelectedSlot(slot);
        }
    }

    /** Only a scroll notch landing within this long after selecting a row counts as "bind scroll to this". */
    private static final long SCROLL_BIND_GRACE_MS = 600L;

    /**
     * Fires the configured scroll action, letting the wheel drive something the way
     * a keybind would.
     *
     * Gated on the mouse being grabbed -- that's this codebase's established
     * "actually playing, no GUI/chat open" check (Minecraft no longer exposes the
     * current screen directly in 26.2). Without it, scrolling a chest or the chat
     * log would fire actions too.
     *
     * GLFW's scroll callback is (window, xOffset, yOffset); yOffset is the vertical
     * notch, positive for up.
     */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void pvpkit$scrollAction(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (yOffset == 0.0) return;
        Minecraft mc = Minecraft.getInstance();

        // Key Binds screen with an action selected: assign the wheel to it, exactly as
        // pressing a key there would -- but only within a short grace window after the
        // row was selected (KeyBindsScreen#lastKeySelection, set by vanilla itself when
        // you click a row). Without that window, EVERY scroll notch while ANY row sits
        // in capture mode gets swallowed as "bind scroll", so the wheel can never be
        // used to just scroll the list to look at other rows -- this was reported as
        // "the scroll wheel is disabled" while setting a keybind. Past the window a
        // scroll falls through to normal list scrolling instead.
        // A "+"-added duplicate row waiting for a key. Checked BEFORE the vanilla-row case
        // below, because that one only ever consults KeyBindsScreen#selectedKey -- which
        // vanilla sets for its OWN rows. A duplicate row is this mod's own widget, so vanilla
        // never sets it and the wheel simply could not be bound to a duplicate at all.
        if (MacroKeyCapture.active()) {
            MacroKeyCapture.accept(ScrollKeybind.SCROLL_KEY);
            if (mc.gui != null && mc.gui.screen() != null) {
                ((ScreenRebuildAccessor) mc.gui.screen()).pvpkit$rebuildWidgets();
            }
            ci.cancel();
            return;
        }

        if (mc.gui != null && mc.gui.screen() instanceof KeyBindsScreen binds && binds.selectedKey != null
                && System.currentTimeMillis() - binds.lastKeySelection <= SCROLL_BIND_GRACE_MS) {
            binds.selectedKey.setKey(ScrollKeybind.SCROLL_KEY);
            binds.selectedKey = null;
            KeyMapping.resetMapping(); // rebuild key -> mapping lookup so it takes effect immediately
            mc.options.save();
            // The row's displayed key name is only refreshed when its button is
            // rebuilt, which normally happens when you click the row again -- forcing
            // that here is what fixed "doesn't show it until you click to change it
            // again" for a scroll-wheel bind specifically.
            ((ScreenRebuildAccessor) binds).pvpkit$rebuildWidgets();
            ci.cancel();
            return;
        }

        if (mc.player == null || !mc.mouseHandler.isMouseGrabbed()) return;
        ScrollKeybind.fire();
        PvpKitConfig c = PvpKitConfig.get();
        ScrollActions.run(yOffset > 0.0 ? c.scrollUpAction : c.scrollDownAction);
    }
}
