package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PracticeBotManager;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the practice bot ACTUALLY TICK, the way Carpet's fake players do.
 *
 * This replaces a long list of hand-written workarounds, and it is the single fix that
 * makes the bot behave like a real player instead of an approximation of one.
 *
 * Fabric's FakePlayer#tick() is a deliberate no-op (its whole body is `return`) -- it's
 * built as a passive utility entity, not a combat one. Everything vanilla normally does
 * per tick therefore never happened: no gravity or physics, no knockback integration,
 * i-frames never decayed (bot became unhittable after one hit), item cooldowns never
 * counted down (an axe-disabled shield stayed disabled forever), the shield's block-delay
 * never advanced (it blocked nothing), and pose/fall-flying maintenance never ran (the bot
 * got stuck rendering flat on its face and was handed elytra GLIDE physics). Each of those
 * was previously patched by hand, and each hand-patch introduced its own subtly wrong
 * behaviour.
 *
 * Carpet solves it at the root -- carpet.patches.EntityPlayerMPFake#tick() is literally
 * `super.tick(); this.doTick();` (verified in its bytecode) -- so its fake players run the
 * complete real player tick. This does exactly the same. `super.tick()` here resolves to
 * ServerPlayer#tick(), which is valid because this mixin is merged into FakePlayer and
 * ServerPlayer is its direct superclass.
 *
 * Scoped strictly to OUR bot: other mods use Fabric's FakePlayer as a non-ticking stand-in
 * for block-breaking and similar, and force-ticking those would be a nasty surprise.
 */
@Mixin(FakePlayer.class)
public abstract class FakePlayerTickMixin extends ServerPlayer {

    /** Never invoked -- Mixin requires it only so this class can declare ServerPlayer as its superclass. */
    private FakePlayerTickMixin(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
        super(server, level, profile, info);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void pvpkit$runTheRealPlayerTick(CallbackInfo ci) {
        if (!PracticeBotManager.isPracticeBot((FakePlayer) (Object) this)) return;
        try {
            PracticeBotManager.countRealTick(); // TEMPORARY: proves this mixin actually runs

            // THE missing half of Carpet's tick, and the reason knockback appeared to do
            // nothing at all. A fake player never sends movement packets, so the connection's
            // "last good position" never advances -- and the server's position-correction
            // logic snaps the player back to it. Any server-side movement (knockback above
            // all) was therefore being reverted almost as fast as it was applied. Carpet does
            // exactly this every 10 ticks to keep the connection's idea of where the player is
            // in step with where the entity actually is.
            if (this.level().getServer().getTickCount() % 10 == 0) {
                this.connection.resetPosition();
                this.level().getChunkSource().move(this);
            }

            // TEMPORARY: knockback demonstrably lands in deltaMovement, then is gone (and the
            // bot hasn't moved) by END_SERVER_TICK. Logging either side of the real tick shows
            // whether the tick consumes it into movement, or wipes it without moving.
            Vec3 before = this.getDeltaMovement();
            super.tick();
            this.doTick();
            Vec3 after = this.getDeltaMovement();
            if (before.horizontalDistanceSqr() > 1.0E-6 || after.horizontalDistanceSqr() > 1.0E-6) {
                PracticeBotManager.logKnockback(String.format(
                        "TICK dmIn=(%.3f, %.3f, %.3f) dmOut=(%.3f, %.3f, %.3f) onGround=%s",
                        before.x, before.y, before.z, after.x, after.y, after.z, this.onGround()));
            }
        } catch (Exception e) {
            // A fake player has no genuine connection behind it, so a stray tick path can
            // still throw. Carpet swallows the same way (its tick() has a catch around this
            // exact pair). Never let it take down the server tick loop.
            PracticeBotManager.onTickError(e);
        }
        ci.cancel();
    }
}
