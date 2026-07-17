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
 * Totem-of-undying hook: both drives the corner pop indicator AND (optionally)
 * hides the big centre-screen animation.
 *
 * GameRenderer#displayItemActivation is the correct 26.2 signal for "the local
 * player just popped a totem": verified against the 26.2 client jar, it is
 * invoked from exactly one place — ClientPacketListener#handleEntityEvent's
 * totem branch (entity event id 35) — and only inside that branch's
 * `entity == minecraft.player` guard. So an invocation here always means our
 * own totem popped, and the totem-stack check below is just belt-and-braces.
 *
 * This replaces the previous detection route (a mixin on
 * LivingEntity#handleEntityEvent watching for byte event 35). That never fired
 * in 26.2: event 35 is intercepted in ClientPacketListener and never falls
 * through to Entity#handleEntityEvent(byte), so the corner pop never triggered
 * even though hiding the centre animation (this same method) worked.
 *
 * This mixin config is REQUIRED (required: true), so if this target were wrong the
 * game would fail to launch with a named error, not silently skip — if you're
 * reading this after a successful launch, that's confirmation the target is correct.
 */
@Mixin(GameRenderer.class)
public class GuiMixin {
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void pvpkit$onTotemActivation(ItemStack stack, CallbackInfo ci) {
        if (!stack.is(Items.TOTEM_OF_UNDYING)) return;
        // Record the pop unconditionally; the HUD render gates on TOTEM_POP itself,
        // so this stays correct whether or not the centre animation is hidden.
        PvpKitClient.triggerTotemPop();
        if (PvpKitClient.HIDE_CENTER_TOTEM) {
            ci.cancel();
        }
    }
}
