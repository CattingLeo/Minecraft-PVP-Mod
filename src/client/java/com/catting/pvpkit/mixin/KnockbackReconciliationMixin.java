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
 * Rather than simply skipping that reset, this reproduces what the absent client would have
 * done: it RECORDS the velocity the motion packet was built from (the post-knockback value)
 * and still lets vanilla's reset run, then FakePlayerTickMixin applies the recorded value at
 * the head of the bot's next tick -- i.e. "the client applies the last motion packet it
 * received", which is exactly the real player's outcome.
 *
 * Recording rather than skipping matters because causeExtraKnockback is called TWICE per hit
 * on some paths (Player#stabAttack, used by the KineticWeapon/PiercingWeapon item components),
 * both times with the SAME oldMovement. Vanilla's reset is what gives the second call a clean
 * baseline; skipping it outright made the second knockback stack on top of the first and sent
 * the bot noticeably further than the same hit would send a real player. On the ordinary
 * Player#attack path (a single call) recording and skipping produce an identical result, so
 * this does not change the melee feel.
 */
@Mixin(Player.class)
public abstract class KnockbackReconciliationMixin {

    @Redirect(method = "causeExtraKnockback",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void pvpkit$keepBotKnockback(Entity instance, Vec3 resetTo) {
        if (PracticeBotManager.isPracticeBot(instance)) {
            // The value the ClientboundSetEntityMotionPacket was just built from -- vanilla
            // constructs the packet immediately before this call, so the current velocity IS
            // what a real client would have been told to apply.
            PracticeBotManager.recordKnockback(instance.getDeltaMovement());
        }
        instance.setDeltaMovement(resetTo);
    }
}
