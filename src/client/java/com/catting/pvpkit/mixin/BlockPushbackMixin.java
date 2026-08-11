package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PracticeBotManager;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * THE reason a shield-mode practice bot got FLUNG TOWARDS you when you hit its raised shield.
 *
 * On a successful block vanilla runs LivingEntity#blockUsingItem, which calls
 * `attacker.blockedByItem(defender, ...)`, whose whole body is:
 *
 *     defender.knockback(0.5, defender.getX() - this.getX(), defender.getZ() - this.getZ(), ...)
 *
 * with `this` being the ATTACKER. That direction vector points from the attacker to the
 * defender, and knockback() SUBTRACTS its direction from the velocity, so the defender is
 * pulled toward whoever just hit their shield -- the opposite of the shield shove it reads
 * like at a glance.
 *
 * A real player never sees this. A fully blocked attack leaves Player#attack's `wasHurt`
 * false, so causeExtraKnockback -- the only thing that would send them a
 * ClientboundSetEntityMotionPacket -- never runs. The velocity is written on the server and
 * then simply overwritten by that player's next movement packet, because their client is
 * authoritative over their own position and was never told about it. It is dead server-side
 * state for anyone with a real connection.
 *
 * Our bot has no client to overrule it, so it actually simulates that velocity and lurches
 * into you. Same shape as the bug KnockbackReconciliationMixin fixes: vanilla writing
 * server-side motion it assumes a client will discard.
 *
 * Cancelled outright rather than reversed, because "a real player wouldn't move here" is the
 * behaviour we're matching, not "a real player would be pushed the other way".
 */
@Mixin(LivingEntity.class)
public abstract class BlockPushbackMixin {

    @Inject(method = "blockedByItem", at = @At("HEAD"), cancellable = true)
    private void pvpkit$dontYankBotIntoAttacker(LivingEntity defender, DamageSource source, float damage, CallbackInfo ci) {
        if (PracticeBotManager.isPracticeBot(defender)) {
            ci.cancel();
        }
    }
}
