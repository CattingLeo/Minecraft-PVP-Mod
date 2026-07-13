package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PvpKitClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cancels ONLY the Slowness FOV zoom — by adding back exactly the FOV that
 * Slowness removed, scaled to the effect level (I, II, ...).
 *
 * How: Slowness reduces movement speed by 15% per level, and the movement FOV
 * modifier is (speed / walkSpeed + 1) / 2. We recompute the modifier as it would
 * be WITHOUT the slowness speed cut and multiply the ratio back in. Because it's
 * a targeted correction (not a clamp), everything else stacks naturally:
 *  - no Slowness -> method untouched (sprint/speed/bow all 100% vanilla)
 *  - bow-draw zoom while slowed -> preserved (its factor survives the ratio)
 *  - Speed + Slowness together -> both contribute correctly
 * Sneaking never changes the FOV modifier in vanilla, so it's unaffected either way.
 */
@Mixin(AbstractClientPlayer.class)
public class FovMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void pvpkit$cancelSlownessZoom(CallbackInfoReturnable<Float> cir) {
        if (!PvpKitClient.NO_SLOWNESS_FOV) return;
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        MobEffectInstance slow = self.getEffect(MobEffects.SLOWNESS);
        if (slow == null) return;

        // Slowness speed multiplier: 15% per level (amplifier 0 = Slowness I)
        float s = 1.0F - 0.15F * (slow.getAmplifier() + 1);
        float original = cir.getReturnValue();
        if (s <= 0.05F) {
            // Slowness so high that speed is (near) zero — exact math degenerates;
            // just neutralise the reduction.
            if (original < 1.0F) cir.setReturnValue(1.0F);
            return;
        }

        try {
            float walk = self.getAbilities().getWalkingSpeed();
            float attr = (float) self.getAttributeValue(Attributes.MOVEMENT_SPEED); // already slowed
            if (walk <= 0.0F) return;
            float with    = (attr / walk + 1.0F) / 2.0F;         // modifier as computed now
            float without = ((attr / s) / walk + 1.0F) / 2.0F;   // modifier without slowness
            if (with <= 0.0F || Float.isNaN(with) || Float.isNaN(without)) return;
            float corrected = original * (without / with);
            // sanity: never let the correction itself go wild
            if (corrected > 0.0F && corrected < 4.0F) {
                cir.setReturnValue(corrected);
            }
        } catch (Throwable ignored) {
            // any attribute API mismatch -> fall back to simple neutralisation
            if (original < 1.0F) cir.setReturnValue(1.0F);
        }
    }
}
