package com.catting.pvpkit;

import java.util.Random;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Combat behaviour for the practice bot. One mode active at a time, driven
 * from PracticeBotManager's END_SERVER_TICK hook (so everything here is
 * already on the server thread).
 *
 * IMPORTANT -- why this is all hand-driven rather than using vanilla AI or
 * physics: `FakePlayer#tick()` is a literal no-op (verified in the Fabric
 * API bytecode: the method body is just `return`). That means the bot has
 * NO gravity, NO collision, NO movement from `setDeltaMovement`, and its
 * attack-strength ticker never recovers. So:
 *   - all movement is manual `setPos` stepping, not velocity;
 *   - attack pacing uses our own tick counters, NOT
 *     `getAttackStrengthScale` (which would stay pinned after the first
 *     `resetAttackStrengthTicker` and never recharge);
 *   - vertical manoeuvres (mace launch, elytra dive) are scripted arcs
 *     rather than real ballistics.
 * Consequence worth knowing: with no collision the bot can clip through
 * terrain while chasing, and it keeps its Y locked to the player's ground
 * level rather than truly falling. It's a sparring dummy, not a pathfinding
 * opponent -- see DEVELOPMENT.md.
 *
 * FakePlayer is also not a `Mob`, so vanilla's goal/pathfinding system
 * isn't available to it at all even in principle.
 */
public final class PracticeBotAi {

    public enum Mode {
        IDLE("idle"),
        SHIELD("shield"),
        SWORD("sword"),
        AXE("axe"),
        MACE("mace"),
        ELYTRA_MACE("elytra mace"),
        FIREWORK_MACE("firework mace"),
        DEFEND("defend"),
        CRYSTAL("crystal");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    // --- tuning ---
    private static final double ENGAGE_RANGE = 24.0;   // ignore the player entirely beyond this
    private static final double MELEE_RANGE = 3.2;
    private static final double MOVE_SPEED = 0.22;     // blocks/tick, ~ sprint speed
    private static final double RETREAT_SPEED = 0.26;  // defend mode backs off slightly faster
    private static final int SWORD_COOLDOWN = 12;      // ticks, mirrors vanilla sword swing recharge
    private static final int AXE_COOLDOWN = 20;        // axes recharge slower in vanilla
    private static final double MACE_ACCURACY = 0.75;  // requested: deliberately imperfect
    private static final int CRYSTAL_COOLDOWN = 30;

    private static final Random RNG = new Random();

    // --- per-bot transient state (single bot, so static is fine) ---
    private static int attackCooldown;
    private static int crystalCooldown;
    private static AirPhase airPhase = AirPhase.GROUND;
    private static int airTimer;
    private static EndCrystal pendingCrystal;

    private enum AirPhase { GROUND, RISING, DIVING }

    private PracticeBotAi() {
    }

    /** Wipes carry-over state so a mode switch never inherits a half-finished mace arc. */
    public static void reset() {
        attackCooldown = 0;
        crystalCooldown = 0;
        airPhase = AirPhase.GROUND;
        airTimer = 0;
        pendingCrystal = null;
    }

    public static void tick(FakePlayer bot, Mode mode, ServerLevel level) {
        if (mode == Mode.IDLE) return;
        if (attackCooldown > 0) attackCooldown--;
        if (crystalCooldown > 0) crystalCooldown--;

        Player target = nearestRealPlayer(bot, level);

        if (mode == Mode.SHIELD) {
            // Pure block-holding dummy: no movement, just keep the shield raised.
            if (!bot.isUsingItem()) bot.startUsingItem(InteractionHand.OFF_HAND);
            return;
        }
        if (target == null) return;

        bot.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());

        switch (mode) {
            case SWORD -> melee(bot, target, SWORD_COOLDOWN);
            case AXE -> melee(bot, target, AXE_COOLDOWN);
            case MACE -> maceArc(bot, target, 12, 0.55, 0.9);
            case ELYTRA_MACE -> maceArc(bot, target, 20, 0.7, 1.0);
            case FIREWORK_MACE -> maceArc(bot, target, 10, 1.1, 1.3);
            case DEFEND -> defend(bot, target);
            case CRYSTAL -> crystal(bot, target, level);
            default -> {
            }
        }
    }

    // ---- modes ----

    private static void melee(FakePlayer bot, Player target, int cooldown) {
        double dist = bot.position().distanceTo(target.position());
        if (dist > MELEE_RANGE) {
            step(bot, target, MOVE_SPEED, true);
        } else if (attackCooldown <= 0) {
            swingAt(bot, target, 1.0);
            attackCooldown = cooldown;
        }
    }

    /**
     * Scripted three-phase mace attack: close in on the ground, launch
     * straight up (the wind-charge/firework "boost" beat), then dive back
     * down onto the player and swing on arrival. Rise/fall speeds and hang
     * time are what distinguish plain mace / elytra mace / firework mace --
     * firework is the fastest and snappiest, elytra the highest and floatiest.
     */
    private static void maceArc(FakePlayer bot, Player target, int riseTicks, double riseSpeed, double diveSpeed) {
        Vec3 pos = bot.position();
        double horizontal = horizontalDistance(pos, target.position());

        switch (airPhase) {
            case GROUND -> {
                if (horizontal > 5.0) {
                    step(bot, target, MOVE_SPEED, true);
                } else {
                    airPhase = AirPhase.RISING;
                    airTimer = riseTicks;
                }
            }
            case RISING -> {
                // Keep tracking the player horizontally on the way up so the dive lands on them.
                trackHorizontally(bot, target, MOVE_SPEED * 0.6);
                bot.setPos(bot.position().x, bot.position().y + riseSpeed, bot.position().z);
                if (--airTimer <= 0) airPhase = AirPhase.DIVING;
            }
            case DIVING -> {
                trackHorizontally(bot, target, MOVE_SPEED);
                double newY = bot.position().y - diveSpeed;
                bot.setPos(bot.position().x, newY, bot.position().z);
                if (newY <= target.position().y + 0.5) {
                    bot.setPos(bot.position().x, target.position().y, bot.position().z);
                    airPhase = AirPhase.GROUND;
                    if (attackCooldown <= 0) {
                        swingAt(bot, target, MACE_ACCURACY);
                        attackCooldown = SWORD_COOLDOWN;
                    }
                }
            }
        }
    }

    /** Backs away, and backs away harder when the player is winding up something big (mace / airborne dive). */
    private static void defend(FakePlayer bot, Player target) {
        double dist = bot.position().distanceTo(target.position());
        boolean threatened = !target.onGround() || target.getMainHandItem().is(net.minecraft.world.item.Items.MACE);
        double keepAway = threatened ? 9.0 : 5.0;
        if (dist < keepAway) {
            step(bot, target, RETREAT_SPEED, false); // false = away from
        }
    }

    /**
     * Places an obsidian block beside the player, puts an end crystal on top,
     * then detonates it on the following pass by attacking it -- the same
     * place/hit loop a real crystal PvP player performs, driven through
     * vanilla's own damage path so the explosion is genuine.
     */
    private static void crystal(FakePlayer bot, Player target, ServerLevel level) {
        if (pendingCrystal != null) {
            if (pendingCrystal.isAlive()) {
                bot.attack(pendingCrystal);
                bot.swing(InteractionHand.MAIN_HAND);
            }
            pendingCrystal = null;
            crystalCooldown = CRYSTAL_COOLDOWN;
            return;
        }
        if (crystalCooldown > 0) return;

        double dist = bot.position().distanceTo(target.position());
        if (dist > 6.0) {
            step(bot, target, MOVE_SPEED, true);
            return;
        }

        // Two blocks to the player's side, at their feet -- close enough that the
        // blast reaches them, offset so the obsidian never replaces their own space.
        BlockPos base = BlockPos.containing(target.position().x + 2.0, target.position().y, target.position().z);
        if (level.getBlockState(base).isAir()) {
            level.setBlockAndUpdate(base, Blocks.OBSIDIAN.defaultBlockState());
        }
        BlockPos above = base.above();
        if (!level.getBlockState(above).isAir()) return;

        EndCrystal crystal = new EndCrystal(level, above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
        crystal.setShowBottom(false);
        level.addFreshEntity(crystal);
        pendingCrystal = crystal;
    }

    // ---- helpers ----

    /** Attacks through vanilla's real damage pipeline, missing (1 - accuracy) of the time. */
    private static void swingAt(FakePlayer bot, Player target, double accuracy) {
        bot.swing(InteractionHand.MAIN_HAND);
        if (accuracy >= 1.0 || RNG.nextDouble() < accuracy) {
            bot.attack(target);
        }
    }

    /** One movement step toward (or away from) the target, keeping Y pinned to the target's level. */
    private static void step(FakePlayer bot, Player target, double speed, boolean toward) {
        Vec3 pos = bot.position();
        Vec3 dest = target.position();
        double dx = dest.x - pos.x;
        double dz = dest.z - pos.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) return;
        double sign = toward ? 1.0 : -1.0;
        bot.setPos(pos.x + (dx / len) * speed * sign, dest.y, pos.z + (dz / len) * speed * sign);
    }

    /** Horizontal-only tracking, leaving Y alone (used mid-air so it doesn't cancel the arc). */
    private static void trackHorizontally(FakePlayer bot, Player target, double speed) {
        Vec3 pos = bot.position();
        double dx = target.position().x - pos.x;
        double dz = target.position().z - pos.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) return;
        double stepLen = Math.min(speed, len);
        bot.setPos(pos.x + (dx / len) * stepLen, pos.y, pos.z + (dz / len) * stepLen);
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
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
