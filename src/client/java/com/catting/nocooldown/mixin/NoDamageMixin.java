package com.catting.nocooldown.mixin;

import com.catting.nocooldown.NoCooldownConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No damage / godmode. Reuses Player#isInvulnerableTo(ServerLevel, DamageSource)
 * -- the exact same check creative-mode players already pass -- rather than
 * intercepting the whole hurt() pipeline.
 *
 * Signature confirmed from an actual 26.2 launch: the game's own Mixin error
 * reported "Expected (ServerLevel, DamageSource, CallbackInfoReturnable)" --
 * this method gained a ServerLevel parameter, matching the same pattern seen
 * elsewhere in this version range (e.g. ItemStack#hurtAndBreak also gained one).
 */
@Mixin(Player.class)
public class NoDamageMixin {
    @Inject(method = "isInvulnerableTo", at = @At("RETURN"), cancellable = true)
    private void nocooldown$noDamage(ServerLevel level, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (NoCooldownConfig.get().noDamage && !cir.getReturnValue()) {
            cir.setReturnValue(true);
        }
    }
}
