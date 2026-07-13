package com.catting.nocooldown.mixin;

import com.catting.nocooldown.NoCooldownConfig;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Infinite hunger. Every hunger-draining action (walking, sprinting, jumping,
 * mining, attacking) works by adding "exhaustion"; once exhaustion crosses a
 * threshold the game converts it into lost saturation/food on the next tick.
 * Rather than guessing at that more complex tick/conversion method, this
 * cancels exhaustion right at its single accumulation point -- same
 * choke-point strategy that worked well for unlimited durability.
 *
 * Paired with a small top-up in NoCooldownClient's tick handler that keeps the
 * bar visually pinned at full while this is on, rather than just freezing it
 * wherever it happened to be when you enabled it.
 */
@Mixin(FoodData.class)
public class HungerMixin {
    @Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
    private void nocooldown$noExhaustion(float exhaustion, CallbackInfo ci) {
        if (NoCooldownConfig.get().infiniteHunger) {
            ci.cancel();
        }
    }
}
