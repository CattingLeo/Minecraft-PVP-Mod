package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PvpKitClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
}
