package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PracticeBotManager;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * THE actual reason the practice bot never visibly took knockback. Confirmed from a real
 * play session's log: KnockbackProbeMixin showed knockback() computing the right velocity
 * every single hit (e.g. dmAfter=(-0.600, 0.400, 0.360)), but the very next END_SERVER_TICK
 * sample already read (0, -0.078, 0) -- gravity only, knockback gone, bot never moved.
 *
 * Player#causeExtraKnockback runs on the ATTACKER immediately after the target's
 * knockback() and has a real-player-vs-real-player special case: for a ServerPlayer
 * target it sends that target's OWN connection a ClientboundSetEntityMotionPacket, then
 * resets the target's server-side deltaMovement back to its PRE-hit value
 * (entity.setDeltaMovement(oldMovement)). That's correct for a genuine connected player --
 * their own client applies the packet, simulates the motion itself, and reports the result
 * back, so the server deliberately does NOT also simulate it (that would double the
 * motion). A FakePlayer has no real client on the other end to receive that packet or ever
 * report movement back, so the reset just deletes the knockback with nothing to replace
 * it, inside the same synchronous attack-handling call that set it -- before
 * FakePlayerTickMixin's tick() ever gets a chance to consume it into actual movement.
 *
 * Redirects the specific setDeltaMovement(Vec3) call vanilla uses for that reset and skips
 * it only when the target is our bot, leaving knockback()'s result in place.
 */
@Mixin(Player.class)
public abstract class KnockbackReconciliationMixin {

    @Redirect(method = "causeExtraKnockback",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void pvpkit$keepBotKnockback(Entity instance, Vec3 resetTo) {
        if (PracticeBotManager.isPracticeBot(instance)) {
            return;
        }
        instance.setDeltaMovement(resetTo);
    }
}
