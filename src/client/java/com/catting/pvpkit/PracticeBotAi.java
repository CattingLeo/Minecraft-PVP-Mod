package com.catting.pvpkit;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Combat behaviour for the practice bot. One mode active at a time, driven from
 * PracticeBotManager's END_SERVER_TICK hook (so everything here is already on the server
 * thread).
 *
 * DELIBERATELY THIN. Everything physical is vanilla's job now: FakePlayerTickMixin runs the
 * real ServerPlayer tick (Carpet's approach), so gravity, collision, friction, knockback
 * integration, i-frame decay, item cooldowns, the shield's block delay, and pose/fall-flying
 * maintenance all happen exactly as they do for a real player.
 *
 * An earlier version of this class hand-rolled every one of those, and every one of them was
 * subtly wrong in a way that produced a visible bug -- invented friction constants that fought
 * vanilla's knockback impulse, a shield that never satisfied its block delay, a bot stuck in
 * the gliding pose being handed elytra physics. All of that is deleted rather than fixed:
 * this class now only decides INTENT (where to look, whether to walk, whether to raise the
 * shield) and leaves the simulation to the game.
 *
 * FakePlayer is not a `Mob`, so vanilla's goal/pathfinding is unavailable even in principle --
 * movement is expressed as the same relative input a real player's WASD produces.
 */
public final class PracticeBotAi {

    public enum Mode {
        IDLE("idle"),
        SHIELD("shield"),
        DEFEND("defend"),
        /** Locked in place: cannot be moved by anything -- knockback, explosions or its own AI. */
        UNMOVEABLE("unmoveable");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    // --- tuning ---
    private static final double ENGAGE_RANGE = 24.0;   // ignore the player entirely beyond this
    private static final double DEFEND_RANGE = 12.0;    // normal keep-away distance
    private static final double DEFEND_RANGE_THREATENED = 18.0; // wound up / airborne player

    /** Where an UNMOVEABLE bot is pinned. Captured on its first tick, cleared on mode change. */
    private static net.minecraft.world.phys.Vec3 lockPos;

    private PracticeBotAi() {
    }

    /** Wipes carry-over state on a mode switch. Vanilla owns the physics state now, so there's nothing left to clear. */
    public static void reset() {
        lockPos = null;
    }

    public static void tick(FakePlayer bot, Mode mode, ServerLevel level) {
        keepFed(bot);
        PracticeBotManager.refreshHealthTag(bot);

        Player target = nearestRealPlayer(bot, level);
        restockTotem(bot);

        if (target != null) {
            // Vanilla only blocks damage arriving from roughly the entity's front, so SHIELD
            // needs this as much as the moving modes do.
            bot.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        }

        if (mode == Mode.SHIELD) holdShield(bot);

        // Movement INPUT, exactly what a real player's WASD sets. vanilla's aiStep() reads
        // these and feeds them to travel() during the real tick -- we never move the bot
        // ourselves. The bot always faces its target, so negative forward = back away.
        //
        // Zeroed during the hit-recoil window (hurtTime), so the bot's own walking
        // acceleration doesn't immediately cancel out the knockback it just took.
        bot.xxa = 0.0f;
        bot.yya = 0.0f;
        bot.zza = (mode == Mode.DEFEND && target != null && bot.hurtTime == 0 && shouldRetreat(bot, target))
                ? -1.0f
                : 0.0f;

        if (mode == Mode.UNMOVEABLE) pinInPlace(bot);
    }

    /**
     * Pins the bot to the spot it was summoned on -- it cannot be shifted by knockback,
     * explosions, water, pistons or anything else.
     *
     * Runs at END_SERVER_TICK, i.e. AFTER vanilla's tick has already moved the entity, so it
     * corrects rather than prevents: velocity is zeroed and the position snapped back the same
     * tick anything displaced it. That ordering is deliberate -- the bot still takes real
     * damage, real hit reactions and real totem pops (all of which happen earlier in the tick),
     * it just never ends a tick anywhere other than where it started.
     *
     * The summon also gives this mode full knockback resistance, so vanilla mostly doesn't try
     * to move it in the first place; this is the backstop that catches everything else.
     */
    private static void pinInPlace(FakePlayer bot) {
        if (lockPos == null) {
            lockPos = bot.position();
            return;
        }
        bot.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        bot.setPos(lockPos.x, lockPos.y, lockPos.z);
        bot.setOnGround(true); // otherwise it reads as perpetually falling and plays fall animations
    }

    /** Backs off, and backs off sooner when the player is winding up something big (airborne / mace out). */
    private static boolean shouldRetreat(FakePlayer bot, Player target) {
        boolean threatened = !target.onGround() || target.getMainHandItem().is(Items.MACE);
        double keepAway = threatened ? DEFEND_RANGE_THREATENED : DEFEND_RANGE;
        return bot.position().distanceTo(target.position()) < keepAway;
    }

    /**
     * Raises the shield, and respects an axe disable.
     *
     * The block delay and the cooldown countdown are vanilla's job again now that the bot
     * really ticks -- the only thing needed here is to not re-raise the shield the instant an
     * axe knocks it down, which would make the axe/shield-break loop impossible to practise.
     */
    private static void holdShield(FakePlayer bot) {
        if (bot.getCooldowns().isOnCooldown(bot.getMainHandItem())) {
            if (bot.isUsingItem()) bot.stopUsingItem();
            return;
        }
        // MAIN hand: the offhand permanently holds the bot's totem (see PracticeBotManager).
        if (!bot.isUsingItem()) bot.startUsingItem(InteractionHand.MAIN_HAND);
    }

    /**
     * Keeps the bot alive the way a real player does -- by popping a totem.
     *
     * The offhand is restocked every tick so the supply is effectively infinite, but each
     * death is a REAL vanilla totem activation: the pop animation plays, health drops to half
     * a heart, and vanilla's own post-totem Regeneration/Absorption kick in. That's far more
     * useful to practise against than damage immunity or silent healing, both of which made
     * hits feel like they weren't landing.
     *
     * Restocking happens AFTER the pop (vanilla consumes the stack during the lethal hit), so
     * there's a one-tick window with an empty offhand. Two lethal hits in a single tick would
     * kill the bot -- attack cooldowns make that unreachable in practice.
     */
    private static void restockTotem(FakePlayer bot) {
        if (!bot.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            bot.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
    }

    /**
     * Food level that neither starves the bot nor lets it quietly heal.
     *
     * Vanilla only regenerates health at food >= 18, and only starves at food == 0, so anything
     * in between is the dead zone this wants. 10 sits squarely in it.
     */
    private static final int FED_LEVEL = 10;

    /**
     * THE reason the bot appeared to "randomly take damage all the time", in every mode.
     *
     * The bot runs the real ServerPlayer tick (see FakePlayerTickMixin), and that includes
     * ServerPlayer#doTick's `foodData.tick(this)`. It never eats, and every hit it takes runs
     * Player#causeFoodExhaustion -- so the more you practise on it, the faster its food drains,
     * until it hits zero and vanilla starves it on a timer forever after. That damage arrives
     * with no attacker and no hit reaction, which also made SHIELD mode look broken: the bot
     * was blocking correctly, but its health kept falling anyway.
     *
     * Pinned rather than topped up, and pinned BELOW the regeneration threshold on purpose.
     * Setting it to a full 20 with saturation would hand the bot passive healing, which is
     * exactly the "hits don't feel like they land" problem the Resistance/Regeneration effects
     * were stripped to avoid.
     */
    private static void keepFed(FakePlayer bot) {
        FoodData food = bot.getFoodData();
        if (food.getFoodLevel() != FED_LEVEL) food.setFoodLevel(FED_LEVEL);
        if (food.getSaturationLevel() != 0.0f) food.setSaturation(0.0f);
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
