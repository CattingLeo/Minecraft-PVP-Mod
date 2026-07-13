package com.catting.nocooldown.mixin;

import com.catting.nocooldown.NoCooldownConfig;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "No cooldown" (mode 3) also removes ITEM-USE cooldowns for everything:
 * ender pearl, wind charge, mace, chorus fruit, shield-after-axe, goat horn, etc.
 * Reports nothing as on cooldown, so items are always usable.
 *
 * (Attack cooldown for fists + all weapons is handled by AttackStrengthMixin.)
 * Fully effective in singleplayer / worlds you host; multiplayer servers enforce
 * their own cooldowns.
 */
@Mixin(ItemCooldowns.class)
public class ItemCooldownMixin {
    @Inject(method = "isOnCooldown", at = @At("HEAD"), cancellable = true)
    private void nocooldown$noItemCooldown(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (NoCooldownConfig.get().mode == NoCooldownConfig.Mode.NO_COOLDOWN) {
            cir.setReturnValue(false);
        }
    }
}
