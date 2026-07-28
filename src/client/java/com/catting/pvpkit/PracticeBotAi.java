package com.catting.pvpkit;

import java.util.Random;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Combat behaviour for the practice bot. One mode active at a time, driven
 * from PracticeBotManager's END_SERVER_TICK hook (so everything here is
 * already on the server thread).
 *
 * IMPORTANT -- why this is all hand-driven rather than using vanilla AI:
 * `FakePlayer#tick()` is a literal no-op (verified in the Fabric API
 * bytecode: the method body is just `return`). Nothing ticks the bot's
 * physics, applies gravity, or recharges its attack-strength meter for us.
 * So:
 *   - gravity is integrated manually (`verticalVelocity` + `getGravity()`),
 *     but movement itself goes through `Entity#move(MoverType.SELF, ...)`,
 *     which runs vanilla's real collision resolution -- so the bot walks
 *     into walls and lands on ground properly instead of clipping through
 *     terrain or teleporting to the player's Y;
 *   - attack pacing uses our own tick counters, NOT
 *     `getAttackStrengthScale` (which would stay pinned after the first
 *     `resetAttackStrengthTicker` and never recharge);
 *   - vertical manoeuvres (mace launch, elytra dive) are scripted arcs
 *     rather than real ballistics, but still collision-checked, so they
 *     abort against ceilings/floors instead of phasing through them.
 *
 * FakePlayer is also not a `Mob`, so vanilla's goal/pathfinding system
 * isn't available to it even in principle -- it walks straight lines and
 * auto-steps single blocks, nothing smarter.
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
    /** Our own vertical velocity, since FakePlayer never ticks its own physics. */
    private static double verticalVelocity;

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
        verticalVelocity = 0.0;
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
            case MACE -> maceArc(bot, target, 12, 0.55, 0.9, false);
            case ELYTRA_MACE -> maceArc(bot, target, 20, 0.7, 1.0, true);
            case FIREWORK_MACE -> maceArc(bot, target, 10, 1.1, 1.3, true);
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
    private static void maceArc(FakePlayer bot, Player target, int riseTicks, double riseSpeed, double diveSpeed, boolean glides) {
        double horizontal = horizontalDistance(bot.position(), target.position());

        switch (airPhase) {
            case GROUND -> {
                setGliding(bot, false);
                if (horizontal > 5.0) {
                    step(bot, target, MOVE_SPEED, true);
                } else {
                    airPhase = AirPhase.RISING;
                    airTimer = riseTicks;
                    verticalVelocity = 0.0;
                }
            }
            case RISING -> {
                // Keep tracking the player horizontally on the way up so the dive lands on them.
                trackHorizontally(bot, target, MOVE_SPEED * 0.6);
                // If a ceiling stops the climb, cut straight to the dive rather than
                // grinding upward against it for the rest of the timer.
                boolean rose = moveVertical(bot, riseSpeed);
                if (!rose || --airTimer <= 0) {
                    airPhase = AirPhase.DIVING;
                    setGliding(bot, glides);
                }
            }
            case DIVING -> {
                trackHorizontally(bot, target, MOVE_SPEED);
                boolean fell = moveVertical(bot, -diveSpeed);
                boolean reachedPlayer = bot.position().y <= target.position().y + 0.5;
                // Landing on terrain (!fell) ends the dive too, so it can't hover
                // forever when the player is standing below an overhang.
                if (!fell || reachedPlayer) {
                    airPhase = AirPhase.GROUND;
                    verticalVelocity = 0.0;
                    setGliding(bot, false);
                    if (attackCooldown <= 0 && horizontalDistance(bot.position(), target.position()) <= MELEE_RANGE) {
                        swingAt(bot, target, MACE_ACCURACY);
                        attackCooldown = SWORD_COOLDOWN;
                    }
                }
            }
        }
    }

    /**
     * Toggles the elytra glide pose. Shared flag 7 is the fall-flying flag
     * (confirmed from LivingEntity#isFallFlying's bytecode); setSharedFlag is
     * protected, opened via src/main/resources/pvpkit.accesswidener because there
     * is no public "start gliding" entry point -- real players only reach it by
     * sending a ServerboundPlayerCommandPacket, which a FakePlayer never does.
     */
    private static void setGliding(FakePlayer bot, boolean gliding) {
        if (bot.isFallFlying() != gliding) {
            bot.setSharedFlag(7, gliding);
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

    /** Horizontal-only tracking with collision, leaving the caller in charge of Y (used mid-arc). */
    private static void trackHorizontally(FakePlayer bot, Player target, double speed) {
        Vec3 pos = bot.position();
        double dx = target.position().x - pos.x;
        double dz = target.position().z - pos.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) return;
        double stepLen = Math.min(speed, len);
        bot.move(MoverType.SELF, new Vec3((dx / len) * stepLen, 0.0, (dz / len) * stepLen));
    }

    /** Vertical-only movement with collision, for the scripted mace arc. Returns true if it actually moved. */
    private static boolean moveVertical(FakePlayer bot, double dy) {
        double before = bot.position().y;
        bot.move(MoverType.SELF, new Vec3(0.0, dy, 0.0));
        return Math.abs(bot.position().y - before) > 1.0e-3;
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
