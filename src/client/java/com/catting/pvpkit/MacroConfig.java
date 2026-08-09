package com.catting.pvpkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Persisted macros (config/macros.json): two fully independent, ordered lists of
 * steps -- one for the "One Tick" tab, one for "Step by Step". A step is a
 * duplicate of a vanilla keybind row, created by the "+" button this mod adds to
 * each row of the REAL vanilla Key Binds screen, and carries its own separately
 * bindable key.
 *
 * Order is just list order: steps are only ever appended or removed, never
 * reordered in place, so list position already IS play order (unlike
 * MultiBindConfig, which needs its own insertion-sequence field).
 */
public class MacroConfig {

    /** One duplicate row: which vanilla KeyMapping it mirrors, plus the key bound to this particular duplicate. */
    public static class Step {
        /** KeyMapping#getName() of the vanilla keybind this row duplicates. */
        public String keyName;
        /** InputConstants key name bound to this duplicate, or unknown while unbound. */
        public String boundKey = "key.keyboard.unknown";
    }

    public List<Step> oneTick = new ArrayList<>();
    public List<Step> stepByStep = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MacroConfig instance;

    public static MacroConfig get() {
        if (instance == null) load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("macros.json");
    }

    public static void load() {
        try {
            Path p = file();
            if (Files.exists(p)) instance = GSON.fromJson(Files.readString(p), MacroConfig.class);
        } catch (Exception ignored) {
        }
        if (instance == null) instance = new MacroConfig();
        if (instance.oneTick == null) instance.oneTick = new ArrayList<>();
        if (instance.stepByStep == null) instance.stepByStep = new ArrayList<>();
        for (Step s : instance.oneTick) if (s.boundKey == null) s.boundKey = "key.keyboard.unknown";
        for (Step s : instance.stepByStep) if (s.boundKey == null) s.boundKey = "key.keyboard.unknown";
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception ignored) {
        }
    }
}
