package com.catting.pvpkit;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Combat behaviour for the practice bot. One mode active at a time, driven
 * from PracticeBotManager's END_SERVER_TICK hook (so everything here is
 * already on the server thread).
 *
 * IMPORTANT -- why this is all hand-driven rather than using vanilla AI:
 * `FakePlayer#tick()` is a literal no-op (verified in the Fabric API
 * bytecode: the method body is just `return`). Nothing ticks the bot's
 * physics or applies gravity for us, so movement integrates its own
 * vertical velocity (`verticalVelocity` + `getGravity()`) but still goes
 * through `Entity#move(MoverType.SELF, ...)` for real collision resolution
 * -- the bot walks into walls and lands on ground properly instead of
 * clipping through terrain or teleporting to the player's Y.
 *
 * FakePlayer is also not a `Mob`, so vanilla's goal/pathfinding system
 * isn't available to it even in principle -- it walks straight lines and
 * auto-steps single blocks, nothing smarter.
 */
public final class PracticeBotAi {

    public enum Mode {
        IDLE("idle"),
        SHIELD("shield"),
        DEFEND("defend");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    // --- tuning ---
    private static final double ENGAGE_RANGE = 24.0;   // ignore the player entirely beyond this
    private static final double RETREAT_SPEED = 0.26;  // defend mode backs off slightly faster
    private static final double DEFEND_RANGE = 12.0;    // normal keep-away distance
    private static final double DEFEND_RANGE_THREATENED = 18.0; // wound up / airborne player

    /** Our own vertical velocity, since FakePlayer never ticks its own physics. */
    private static double verticalVelocity;

    private PracticeBotAi() {
    }

    /** Wipes carry-over state on a mode switch. */
    public static void reset() {
        verticalVelocity = 0.0;
    }

    public static void tick(FakePlayer bot, Mode mode, ServerLevel level) {
        // MUST run before the IDLE early-return, and every tick regardless of mode --
        // see keepHittable()'s javadoc. This being mode-gated is exactly why the plain
        // `/practicebot` dummy became unhittable after a single hit.
        keepHittable(bot);

        if (mode == Mode.IDLE) return;

        Player target = nearestRealPlayer(bot, level);
        if (target == null) return;

        // Face the player every tick regardless of mode -- SHIELD needs this too, since
        // vanilla only blocks damage arriving from roughly the entity's front; without it
        // the bot keeps facing wherever it spawned and the shield stops blocking the
        // instant the player moves to a different angle.
        bot.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());

        switch (mode) {
            case SHIELD -> {
                if (!bot.isUsingItem()) bot.startUsingItem(InteractionHand.OFF_HAND);
            }
            case DEFEND -> defend(bot, target);
            default -> {
            }
        }
    }

    /**
     * THE fix for "I can't hit him". Vanilla's damage entry point
     * (`LivingEntity#hurt`) rejects any hit while `invulnerableTime > 0` --
     * that's the normal ~0.5s i-frame window after every hit. Those counters
     * are decremented in `Entity#tick`/`LivingEntity#tick`... which for a
     * FakePlayer is a literal no-op. So the very first hit set
     * `invulnerableTime = 20` and nothing ever brought it back down, leaving
     * the bot permanently unhittable from then on. Both fields are public, so
     * we just tick them down ourselves.
     *
     * Also keeps the bot alive by topping health back up when it gets low,
     * rather than making it damage-immune. The previous approach (Resistance
     * amplifier 255) reduced incoming damage to a flat 0, which meant hits
     * registered no damage AND no knockback -- indistinguishable from the
     * setInvulnerable(true) it was meant to replace. Letting damage land for
     * real and healing afterwards keeps it genuinely unkillable while hits,
     * knockback and hit animations all behave normally, and you can still see
     * the health bar move so you know you're connecting.
     */
    private static void keepHittable(FakePlayer bot) {
        if (bot.invulnerableTime > 0) bot.invulnerableTime--;
        if (bot.hurtTime > 0) bot.hurtTime--;

        float max = bot.getMaxHealth();
        if (bot.getHealth() < max * 0.4f) {
            bot.setHealth(max);
        }
    }

    // ---- modes ----

    /** Backs away, and backs away harder when the player is winding up something big (airborne / mace out). */
    private static void defend(FakePlayer bot, Player target) {
        double dist = bot.position().distanceTo(target.position());
        boolean threatened = !target.onGround() || target.getMainHandItem().is(net.minecraft.world.item.Items.MACE);
        double keepAway = threatened ? DEFEND_RANGE_THREATENED : DEFEND_RANGE;
        if (dist < keepAway) {
            step(bot, target, RETREAT_SPEED, false); // false = away from
        }
    }

    // ---- helpers ----

    /**
     * One collision-aware movement step toward (or away from) the target, letting
     * gravity handle the vertical axis.
     *
     * Uses `Entity#move(MoverType.SELF, ...)` rather than `setPos`: move() runs
     * vanilla's full collision resolution (so the bot walks into walls instead of
     * clipping through them, and lands on ground instead of sinking) and updates
     * `onGround()` as a side effect. Because FakePlayer#tick() is a no-op, nothing
     * else applies gravity for us, so we integrate our own vertical velocity here.
     */
    private static void step(FakePlayer bot, Player target, double speed, boolean toward) {
        Vec3 pos = bot.position();
        Vec3 dest = target.position();
        double dx = dest.x - pos.x;
        double dz = dest.z - pos.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        double sign = toward ? 1.0 : -1.0;
        double mx = len < 1.0e-4 ? 0.0 : (dx / len) * speed * sign;
        double mz = len < 1.0e-4 ? 0.0 : (dz / len) * speed * sign;
        moveWithGravity(bot, mx, mz);
    }

    /** Applies gravity + a horizontal step through vanilla collision. Also auto-steps up single blocks. */
    private static void moveWithGravity(FakePlayer bot, double mx, double mz) {
        if (bot.onGround()) {
            verticalVelocity = 0.0;
        } else {
            verticalVelocity -= bot.getGravity();
            if (verticalVelocity < -3.0) verticalVelocity = -3.0; // terminal velocity clamp
        }

        double beforeX = bot.position().x;
        double beforeZ = bot.position().z;
        bot.move(MoverType.SELF, new Vec3(mx, verticalVelocity, mz));

        // If collision fully blocked the horizontal step while grounded, try a single-block
        // step up -- otherwise the bot gets permanently stuck on any 1-high ledge, since it
        // has no jump input of its own.
        boolean blocked = Math.abs(bot.position().x - beforeX) < 1.0e-3
                && Math.abs(bot.position().z - beforeZ) < 1.0e-3;
        if (blocked && bot.onGround() && (mx != 0.0 || mz != 0.0)) {
            bot.move(MoverType.SELF, new Vec3(0.0, 1.0, 0.0));
            bot.move(MoverType.SELF, new Vec3(mx, 0.0, mz));
        }
    }

    /** Nearest real player, excluding the bot itself (it lives in level.players() too). */
    private static Player nearestRealPlayer(FakePlayer bot, ServerLevel level) {
        Player best = null;
        double bestDist = ENGAGE_RANGE * ENGAGE_RANGE;
        for (Player p : level.players()) {
            if (p.getUUID().equals(bot.getUUID())) continue;
            double d = p.position().distanceToSqr(bot.position());
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
