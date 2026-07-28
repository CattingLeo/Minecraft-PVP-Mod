package com.catting.pvpkit;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Remembers whether a practice bot should exist, and where, across world
 * rejoins/game restarts -- config/practicebot.json.
 *
 * Needed because the bot's entity itself does NOT persist on its own:
 * Player#shouldBeSaved() unconditionally returns false (verified in the
 * 26.2 client jar -- `iconst_0; ireturn`), which excludes ALL player-type
 * entities (including FakePlayer, which extends ServerPlayer) from
 * vanilla's normal per-chunk entity saving -- real players are saved via a
 * completely separate playerdata-file system tied to a real login, which a
 * FakePlayer never goes through. Combined with FakePlayer's own cache
 * (FAKE_PLAYER_MAP) being purely in-memory and rebuilt empty every server
 * start, the bot is fully gone -- not just its effects -- the moment you
 * quit and reload. See PracticeBotManager's SERVER_STARTED listener, which
 * reads this state and re-summons from scratch if `active`.
 */
public class PracticeBotState {

    public boolean active = false;
    public boolean shield = false;
    public String dimension = "minecraft:overworld";
    public double x;
    public double y;
    public double z;
    public float yaw;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PracticeBotState instance;

    public static PracticeBotState get() {
        if (instance == null) load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("practicebot.json");
    }

    public static void load() {
        try {
            Path p = file();
            if (Files.exists(p)) instance = GSON.fromJson(Files.readString(p), PracticeBotState.class);
        } catch (Exception ignored) {
        }
        if (instance == null) instance = new PracticeBotState();
        if (instance.dimension == null) instance.dimension = "minecraft:overworld";
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception ignored) {
        }
    }
}
