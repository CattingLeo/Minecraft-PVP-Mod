package com.catting.pvpkit.mixin;

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
        // pressing a key there would. Cancelled so the same notch doesn't also scroll
        // the list out from under the row being bound. Gui#screen() is 26.2's accessor
        // -- Minecraft no longer exposes `screen` as a field.
        if (mc.gui != null && mc.gui.screen() instanceof KeyBindsScreen binds && binds.selectedKey != null) {
            binds.selectedKey.setKey(ScrollKeybind.SCROLL_KEY);
            binds.selectedKey = null;
            KeyMapping.resetMapping(); // rebuild key -> mapping lookup so it takes effect immediately
            mc.options.save();
            ci.cancel();
            return;
        }

        if (mc.player == null || !mc.mouseHandler.isMouseGrabbed()) return;
        ScrollKeybind.fire();
        PvpKitConfig c = PvpKitConfig.get();
        ScrollActions.run(yOffset > 0.0 ? c.scrollUpAction : c.scrollDownAction);
    }
}
