package com.catting.nocooldown.mixin;

import com.catting.nocooldown.NoCooldownConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Instant use: forces the "how many ticks to fully use this item" duration to 1
 * for everything that uses it (food, potions, bow draw, crossbow load, shield
 * raise). Using 1 rather than 0 to avoid edge cases in code that divides by the
 * use-duration for charge-percentage calculations (e.g. bow pull animation).
 */
@Mixin(Item.class)
public class UseDurationMixin {
    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true)
    private void nocooldown$instantUse(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (NoCooldownConfig.get().instantUse && cir.getReturnValue() > 1) {
            cir.setReturnValue(1);
        }
    }
}
