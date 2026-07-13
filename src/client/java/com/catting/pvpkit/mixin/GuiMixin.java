package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PvpKitClient;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the big centre-screen totem animation.
 *
 * displayItemActivation lives on GameRenderer (not Gui — confirmed against 26.x's
 * HUD/GUI rendering split, which moved world/camera-space effects like this off
 * the Gui class).
 *
 * This mixin config is REQUIRED (required: true), so if this target were wrong the
 * game would fail to launch with a named error, not silently skip — if you're
 * reading this after a successful launch, that's confirmation the target is correct.
 */
@Mixin(GameRenderer.class)
public class GuiMixin {
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void pvpkit$hideCentreTotem(ItemStack stack, CallbackInfo ci) {
        if (PvpKitClient.HIDE_CENTER_TOTEM && stack.is(Items.TOTEM_OF_UNDYING)) {
            ci.cancel();
        }
    }
}
