package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PracticeBotManager;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TEMPORARY DIAGNOSTIC. Logs vanilla's knockback() for the practice bot at both HEAD and
 * RETURN, so we can see with certainty whether knockback is called at all, what strength it
 * receives, and what it actually writes into deltaMovement.
 *
 * Needed because the END_SERVER_TICK sampling only ever showed deltaMovement AFTER the whole
 * entity tick had run -- which cannot distinguish "knockback never happened" from "knockback
 * happened and something later wiped it". This probe sits inside the call itself. Delete once
 * the knockback behaviour is resolved.
 */
@Mixin(LivingEntity.class)
public abstract class KnockbackProbeMixin {

    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"))
    private void pvpkit$knockbackIn(double strength, double x, double z, DamageSource source,
                                    float f, boolean b, CallbackInfo ci) {
        if (!PracticeBotManager.isPracticeBot(this)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        PracticeBotManager.logKnockback(String.format(
                "KB in  strength=%.3f dir=(%.3f, %.3f) dmBefore=%s onGround=%s src=%s CALLER=%s",
                strength, x, z, fmt(self.getDeltaMovement()), self.onGround(),
                source == null ? "null" : source.getMsgId(), caller()));
    }

    /** Which vanilla method invoked knockback -- the two calls per hit push opposite ways and cancel. */
    @org.spongepowered.asm.mixin.Unique
    private static String caller() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : st) {
            String m = e.getClassName() + "." + e.getMethodName();
            if (m.contains("getStackTrace") || m.contains("KnockbackProbeMixin") || m.contains("knockback")) continue;
            sb.append(e.getMethodName()).append('<');
            if (sb.length() > 90) break;
        }
        return sb.toString();
    }

    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("RETURN"))
    private void pvpkit$knockbackOut(double strength, double x, double z, DamageSource source,
                                     float f, boolean b, CallbackInfo ci) {
        if (!PracticeBotManager.isPracticeBot(this)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        PracticeBotManager.logKnockback("KB out dmAfter=" + fmt(self.getDeltaMovement()));
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
    }
}
