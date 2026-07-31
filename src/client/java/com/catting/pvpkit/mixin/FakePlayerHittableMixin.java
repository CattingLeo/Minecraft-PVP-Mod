package com.catting.pvpkit.mixin;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * THE actual reason the practice bot was never truly hittable, despite every
 * previous fix (invulnerableTime/hurtTime decay in PracticeBotAi#keepHittable,
 * clearing Resistance, setInvulnerable(false)): Fabric API's own FakePlayer
 * hardcodes isInvulnerableTo(level, source) to unconditionally `return true`
 * (verified in its bytecode -- the whole method body is `iconst_1; ireturn`).
 * That's an early gate in the vanilla damage pipeline, checked before
 * invulnerableTime, Resistance, or anything else in PracticeBotAi is ever
 * consulted -- every one of those fixes was tuning state downstream of a check
 * that always short-circuited first. Fabric ships FakePlayer this way on
 * purpose (it's meant as a persistent, non-combat utility entity), which is
 * exactly why the vanilla `Player#isInvulnerableTo` behavior needs to be
 * restored here for it to work as a hittable combat dummy at all.
 */
@Mixin(FakePlayer.class)
public abstract class FakePlayerHittableMixin {

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void pvpkit$hittable(ServerLevel level, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
