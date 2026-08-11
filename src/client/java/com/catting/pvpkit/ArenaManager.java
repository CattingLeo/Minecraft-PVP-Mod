package com.catting.pvpkit;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * /arena -- builds a practice duel arena around you, in the shape every practice server uses:
 * a flat walled box with two opposing spawn pods, a countdown that holds you in place, and
 * gates that drop when it hits GO.
 *
 * The layout follows the duel-server convention rather than a copy of any one server's build
 * (no public schematic for those exists): a clean flat floor with no cover so nothing decides
 * the fight but the fight, a wall you can't leave, and the two spawns directly opposite each
 * other at equal distance.
 *
 * /arena         builds it centred on you
 * /arena start   pods you and the practice bot up, counts 3-2-1, then opens the gates
 * /arena remove  puts the terrain back exactly as it was
 *
 * SINGLEPLAYER / WORLD-HOST ONLY, same as the practice bot -- placing blocks needs server
 * authority, which a client-side mod only has when you ARE the server.
 *
 * Every block replaced is snapshotted first (see `original`), so removing the arena restores
 * what was underneath instead of leaving a quartz scar in your world. That snapshot lives in
 * memory only: it's thousands of block states, which is not something to write to disk every
 * time, so a restart forfeits the restore -- ArenaState remembers the bounds so /arena remove
 * can still clear it flat afterwards.
 */
public final class ArenaManager {

    /**
     * The arena shapes practice servers actually run. Each one exists because it drills a
     * different skill, which is why they're different builds rather than reskins:
     *
     *   REGULAR  flat walled box, no cover -- straight duelling, nothing but the fight decides it
     *   SUMO     small platform ringed by a pit -- knockback control; falling off loses
     *   BRIDGES  two platforms with a gap between them -- fighting on and over an edge
     *   SPLEEF   breakable snow floor over a pit -- the floor itself is the weapon
     */
    public enum Arena {
        REGULAR("regular"),
        SUMO("sumo"),
        BRIDGES("bridges"),
        SPLEEF("spleef");

        public final String label;

        Arena(String label) {
            this.label = label;
        }
    }

    // --- shape ---
    private static final int RADIUS = 12;       // 25x25 floor
    private static final int WALL_HEIGHT = 5;
    private static final int POD_DEPTH = 2;     // how far the spawn pod is inset from the wall
    private static final int PIT_DEPTH = 8;     // how far you drop off a sumo/bridges/spleef floor

    private static final int COUNTDOWN_TICKS = 20; // one second per number

    private record Snapshot(BlockPos pos, BlockState state) {
    }

    /** What was here before the arena, in placement order. Empty when no arena is built. */
    private static final List<Snapshot> original = new ArrayList<>();
    /** The two gate columns, dropped when the countdown ends. */
    private static final List<BlockPos> gateBlocks = new ArrayList<>();

    private static BlockPos centre;
    private static BlockPos podA;
    private static BlockPos podB;
    /** Where the arena actually is -- hardcoding the overworld would break one built in the nether. */
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> arenaDimension;

    private static int countdownEndsAt;
    private static int lastNumberShown;

    private ArenaManager() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            // A type is always required, same as /practicebot needs a mode -- bare "/arena"
            // would have to silently pick one for you.
            var root = ClientCommands.literal("arena")
                    .then(ClientCommands.literal("start").executes(ctx -> start(ctx.getSource())))
                    .then(ClientCommands.literal("remove").executes(ctx -> remove(ctx.getSource())));
            for (Arena arena : Arena.values()) {
                root = root.then(ClientCommands.literal(arena.label)
                        .executes(ctx -> build(ctx.getSource(), arena)));
            }
            dispatcher.register(root);
        });

        ServerTickEvents.END_SERVER_TICK.register(ArenaManager::tickCountdown);
    }

    // ---- building -------------------------------------------------------------------

    private static int build(FabricClientCommandSource source, Arena arena) {
        Minecraft mc = source.getClient();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            source.sendError(Component.literal(
                    "/arena needs you to be the world host (singleplayer, or \"Open to LAN\")."));
            return 0;
        }
        BlockPos playerPos = source.getPlayer().blockPosition();
        server.execute(() -> {
            ServerLevel level = server.getLevel(source.getPlayer().level().dimension());
            if (level == null) return;
            if (!original.isEmpty()) clearArena(level); // one arena at a time; swap cleanly
            buildArena(level, playerPos, arena);
            mc.execute(() -> source.sendFeedback(Component.literal(
                    arena.label + " arena built. /arena start to duel, /arena remove to put the ground back.")));
        });
        return 1;
    }

    /**
     * Lays the floor, walls and both pods.
     *
     * Built from the player's feet DOWN by one, so the floor lands where you're standing rather
     * than burying you in quartz.
     */
    private static void buildArena(ServerLevel level, BlockPos playerPos, Arena arena) {
        original.clear();
        gateBlocks.clear();
        centre = playerPos;
        arenaDimension = level.dimension();

        int floorY = playerPos.getY() - 1;
        switch (arena) {
            case REGULAR -> buildRegular(level, floorY);
            case SUMO -> buildSumo(level, floorY);
            case BRIDGES -> buildBridges(level, floorY);
            case SPLEEF -> buildSpleef(level, floorY);
        }
        buildPod(level, podA, true);
        buildPod(level, podB, false);
    }

    /** Flat walled box, no cover -- the straight duelling arena. */
    private static void buildRegular(ServerLevel level, int floorY) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                boolean edge = Math.abs(x) == RADIUS || Math.abs(z) == RADIUS;
                place(level, new BlockPos(centre.getX() + x, floorY, centre.getZ() + z),
                        edge ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : Blocks.QUARTZ_BLOCK.defaultBlockState());

                for (int y = 1; y <= WALL_HEIGHT; y++) {
                    BlockPos pos = new BlockPos(centre.getX() + x, floorY + y, centre.getZ() + z);
                    if (edge) {
                        // Solid to head height, glass above so it doesn't feel like a pit.
                        place(level, pos, y <= 2
                                ? Blocks.SMOOTH_QUARTZ.defaultBlockState()
                                : Blocks.GLASS.defaultBlockState());
                    } else {
                        place(level, pos, Blocks.AIR.defaultBlockState()); // clear the play space
                    }
                }
            }
        }
        // Corner pillars, purely so the box reads as a build rather than a bug.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = 1; y <= WALL_HEIGHT; y++) {
                    place(level, new BlockPos(centre.getX() + sx * RADIUS, floorY + y, centre.getZ() + sz * RADIUS),
                            Blocks.QUARTZ_PILLAR.defaultBlockState());
                }
            }
        }
        podA = new BlockPos(centre.getX(), floorY + 1, centre.getZ() - RADIUS + POD_DEPTH);
        podB = new BlockPos(centre.getX(), floorY + 1, centre.getZ() + RADIUS - POD_DEPTH);
    }

    /**
     * Sumo: a small platform with nothing around it. Deliberately has NO wall -- knocking the
     * other one off is the entire game, so a wall would defeat the point.
     */
    private static void buildSumo(ServerLevel level, int floorY) {
        int half = 5; // 11x11, small enough that knockback decides it
        digPit(level, floorY, half + 5);
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean rim = Math.abs(x) == half || Math.abs(z) == half;
                place(level, new BlockPos(centre.getX() + x, floorY, centre.getZ() + z),
                        rim ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : Blocks.QUARTZ_BLOCK.defaultBlockState());
            }
        }
        podA = new BlockPos(centre.getX(), floorY + 1, centre.getZ() - half + 1);
        podB = new BlockPos(centre.getX(), floorY + 1, centre.getZ() + half - 1);
    }

    /** Bridges: two platforms with a gap between them, so the fight happens on and over an edge. */
    private static void buildBridges(ServerLevel level, int floorY) {
        int half = 6;      // each platform is 13 wide
        int gap = 9;       // empty space between their inner edges
        int offset = gap / 2 + half;

        digPit(level, floorY, offset + half + 3);
        for (int side = -1; side <= 1; side += 2) {
            int centreZ = centre.getZ() + side * offset;
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    boolean rim = Math.abs(x) == half || Math.abs(z) == half;
                    place(level, new BlockPos(centre.getX() + x, floorY, centreZ + z),
                            rim ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState());
                }
            }
        }
        podA = new BlockPos(centre.getX(), floorY + 1, centre.getZ() - offset - half + 2);
        podB = new BlockPos(centre.getX(), floorY + 1, centre.getZ() + offset + half - 2);
    }

    /** Spleef: a breakable snow floor over a drop -- the floor is the weapon. */
    private static void buildSpleef(ServerLevel level, int floorY) {
        int half = 10;
        digPit(level, floorY, half + 2);
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                place(level, new BlockPos(centre.getX() + x, floorY, centre.getZ() + z),
                        Blocks.SNOW_BLOCK.defaultBlockState());
                // A low wall so you fall THROUGH the floor rather than merely walking off it.
                if (Math.abs(x) == half || Math.abs(z) == half) {
                    for (int y = 1; y <= 3; y++) {
                        place(level, new BlockPos(centre.getX() + x, floorY + y, centre.getZ() + z),
                                Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
            }
        }
        podA = new BlockPos(centre.getX(), floorY + 1, centre.getZ() - half + 2);
        podB = new BlockPos(centre.getX(), floorY + 1, centre.getZ() + half - 2);
    }

    /**
     * Hollows out the drop beneath a floating arena, and the headroom above it.
     *
     * Without this a sumo platform built on flat ground would just drop you 1 block onto grass,
     * which isn't a loss condition. PIT_DEPTH is enough to hurt and to read as "you're out"
     * without being a death sentence you have to walk back from.
     */
    private static void digPit(ServerLevel level, int floorY, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -PIT_DEPTH; y <= WALL_HEIGHT; y++) {
                    if (y == 0) continue; // the floor itself is placed by the caller
                    place(level, new BlockPos(centre.getX() + x, floorY + y, centre.getZ() + z),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /**
     * A 3-wide spawn pod with a gate facing the middle.
     *
     * The gate is iron bars rather than glass so it reads as a gate at a glance, and it's the
     * only part of the arena that gets removed on start -- see openGates.
     */
    private static void buildPod(ServerLevel level, BlockPos pod, boolean facingPositiveZ) {
        int gateZ = pod.getZ() + (facingPositiveZ ? 1 : -1);
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 3; y++) {
                BlockPos side = new BlockPos(pod.getX() + x, pod.getY() + y, pod.getZ());
                if (x != 0) place(level, side, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());

                BlockPos gate = new BlockPos(pod.getX() + x, pod.getY() + y, gateZ);
                place(level, gate, Blocks.IRON_BARS.defaultBlockState());
                gateBlocks.add(gate);
            }
        }
    }

    /** Records what was there, then places. Everything the arena writes goes through here. */
    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        original.add(new Snapshot(pos.immutable(), level.getBlockState(pos)));
        level.setBlockAndUpdate(pos, state);
    }

    // ---- the duel -------------------------------------------------------------------

    private static int start(FabricClientCommandSource source) {
        Minecraft mc = source.getClient();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || original.isEmpty()) {
            source.sendError(Component.literal("Build an arena first with /arena."));
            return 0;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
            if (player == null) return;
            player.teleportTo(podA.getX() + 0.5, podA.getY(), podA.getZ() + 0.5);
            PracticeBotManager.moveToArena(podB);
            countdownEndsAt = server.getTickCount() + COUNTDOWN_TICKS * 3;
            lastNumberShown = -1;
        });
        return 1;
    }

    /**
     * Drives 3-2-1-GO, then drops the gates.
     *
     * Deliberately does NOT freeze anyone: the pods already hold both fighters, which is the
     * same thing practice servers achieve with a barrier, and doing it with blocks means there
     * is no movement-blocking state left to leak if something goes wrong mid-countdown.
     */
    private static void tickCountdown(MinecraftServer server) {
        if (countdownEndsAt == 0) return;

        int remaining = countdownEndsAt - server.getTickCount();
        if (remaining <= 0) {
            countdownEndsAt = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                title(player, Component.literal("GO").withStyle(ChatFormatting.GREEN), Component.empty());
                player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(),
                        SoundSource.MASTER, 1.0f, 1.6f);
            }
            openGates(server);
            return;
        }

        int number = (remaining + COUNTDOWN_TICKS - 1) / COUNTDOWN_TICKS;
        if (number == lastNumberShown) return;
        lastNumberShown = number;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            title(player, Component.literal(String.valueOf(number)).withStyle(ChatFormatting.GOLD),
                    Component.literal("Get ready").withStyle(ChatFormatting.GRAY));
            player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(),
                    SoundSource.MASTER, 1.0f, 1.0f);
        }
    }

    /** The "gate opening" -- the bars simply vanish, which is all it has to do. */
    private static void openGates(MinecraftServer server) {
        if (arenaDimension == null) return;
        ServerLevel level = server.getLevel(arenaDimension);
        if (level == null) return;
        for (BlockPos gate : gateBlocks) {
            level.setBlockAndUpdate(gate, Blocks.AIR.defaultBlockState());
        }
        gateBlocks.clear();
    }

    private static void title(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    // ---- teardown -------------------------------------------------------------------

    private static int remove(FabricClientCommandSource source) {
        Minecraft mc = source.getClient();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || original.isEmpty()) {
            source.sendError(Component.literal("No arena to remove."));
            return 0;
        }
        server.execute(() -> {
            ServerLevel level = server.getLevel(source.getPlayer().level().dimension());
            if (level != null) clearArena(level);
            mc.execute(() -> source.sendFeedback(Component.literal("Arena removed, ground restored.")));
        });
        return 1;
    }

    /** Restores in REVERSE placement order, so overlapping writes unwind to the true original. */
    private static void clearArena(ServerLevel level) {
        for (int i = original.size() - 1; i >= 0; i--) {
            Snapshot snapshot = original.get(i);
            level.setBlockAndUpdate(snapshot.pos(), snapshot.state());
        }
        original.clear();
        gateBlocks.clear();
        countdownEndsAt = 0;
    }
}
