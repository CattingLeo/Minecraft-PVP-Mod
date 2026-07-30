package com.catting.pvpkit;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Persisted settings for PvP Kit (config/pvpkit.json). Edited via Mod Menu -> PvP Kit. */
public class PvpKitConfig {

    public enum TotemCorner {
        TOP_LEFT("Top-left"),
        TOP_RIGHT("Top-right"),
        BOTTOM_LEFT("Bottom-left"),
        BOTTOM_RIGHT("Bottom-right");

        public final String label;
        TotemCorner(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    // --- HUD ---
    public boolean showFps = true;
    public boolean showCps = true;
    public boolean showPing = true;
    public boolean rainbowText = true;
    public int hudX = 6;      // negative = from opposite edge
    public int hudY = -28;    // negative = from bottom

    // --- Totem ---
    public boolean totemPop = true;
    public boolean hideCenterTotem = true;
    public TotemCorner totemCorner = TotemCorner.TOP_RIGHT;
    public int totemSizePct = 160;  // 100 = 16px
    public boolean totemFlash = true;

    // --- Clean view ---
    public boolean noSlownessFov = true;
    public boolean noNausea = true;
    public boolean noHurtTilt = true;
    public boolean noDarkness = true;
    public boolean noBlindness = true;
    public boolean fullbright = true;

    // --- Locator (points to ONE named player; see PvpKitClient javadoc for fair-use note) ---
    public boolean locatorEnabled = false;
    public boolean locatorDisableInSpectator = true;
    public boolean locatorShowThroughWalls = false;

    // --- Combat ---
    public boolean noCrystalExplosion = true;
    public boolean cooldownFlash = true;
    public boolean showHitMarker = true;
    public boolean hotbarSwapFlash = true;
    public boolean disableScrollHotbar = false;

    // --- Utility (see PvpKitClient javadoc for how each one works) ---
    public boolean autoTotem = false;
    public boolean autoEat = false;
    public int autoEatHungerThreshold = 14;
    public boolean criticals = false;
    public boolean moduleHud = true;

    // ------------------------------------------------------------------
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PvpKitConfig instance;

    public static PvpKitConfig get() {
        if (instance == null) load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvpkit.json");
    }

    public static void load() {
        try {
            Path p = file();
            if (Files.exists(p)) instance = GSON.fromJson(Files.readString(p), PvpKitConfig.class);
        } catch (Exception ignored) {
        }
        if (instance == null) instance = new PvpKitConfig();
        if (instance.totemCorner == null) instance.totemCorner = TotemCorner.TOP_RIGHT;
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception ignored) {
        }
    }
}
