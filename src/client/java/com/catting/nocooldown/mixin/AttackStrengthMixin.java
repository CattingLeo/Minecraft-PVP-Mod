package com.catting.nocooldown.mixin;

import com.catting.nocooldown.NoCooldownConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces the attack-strength charge to 100%.
 *
 * In Java Edition the spear's jab uses a forced variant of the normal attack
 * cooldown (the player cannot attack until the charge reaches 100%), so both
 * "no spear cooldown" and "no cooldown" run through this one method:
 *   - NO_COOLDOWN: always report full charge (Bedrock-style, everything).
 *   - NO_SPEAR_COOLDOWN: full charge only while holding a spear.
 *
 * Spears are matched by registry id path ("...spear"), which covers every tier
 * without depending on the SpearItem class name.
 */
@Mixin(Player.class)
public class AttackStrengthMixin {

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void nocooldown$forceFullCharge(float baseTime, CallbackInfoReturnable<Float> cir) {
        NoCooldownConfig.Mode mode = NoCooldownConfig.get().mode;
        if (mode == NoCooldownConfig.Mode.DISABLED) {
            return;
        }
        if (mode == NoCooldownConfig.Mode.NO_COOLDOWN) {
            cir.setReturnValue(1.0F);
            return;
        }
        // NO_SPEAR_COOLDOWN
        Player self = (Player) (Object) this;
        if (isSpear(self.getMainHandItem())) {
            cir.setReturnValue(1.0F);
        }
    }

    private static boolean isSpear(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("spear");
    }
}
