package com.catting.nocooldown.mixin;

import com.catting.nocooldown.NoCooldownConfig;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unlimited durability. ItemStack#hurtAndBreak has THREE overloads in current
 * mappings (differing by ServerLevel/ServerPlayer/LivingEntity/EquipmentSlot
 * params), which is an ambiguous, error-prone mixin target. Every one of those
 * overloads ultimately writes the result through the single-signature
 * ItemStack#setDamageValue(int), so we cancel there instead -- one clean choke
 * point, no overload-disambiguation risk.
 */
@Mixin(ItemStack.class)
public class DurabilityMixin {
    @Inject(method = "setDamageValue", at = @At("HEAD"), cancellable = true)
    private void nocooldown$noDurabilityLoss(int damage, CallbackInfo ci) {
        if (NoCooldownConfig.get().unlimitedDurability) {
            ci.cancel();
        }
    }
}
