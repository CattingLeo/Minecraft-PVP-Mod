package com.catting.pvpkit;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/** Persisted settings for PvP Kit (config/pvpkit.json). Edited via Mod Menu -> PvP Kit. */
public class PvpKitConfig {

    /**
     * What a scroll-wheel notch does, so the wheel can drive an action the way a
     * keybind would. Done as a config choice rather than a real bindable key
     * because the vanilla Key Binds screen can't capture a scroll event: in 26.2
     * `mouseScrolled` is a default interface method that screen never overrides,
     * so making it bindable there would mean merging an override that replaces the
     * inherited dispatch which scrolls the keybind list itself.
     */
    public enum ScrollAction {
        NONE("None"),
        HOTBAR_NEXT("Hotbar: next slot"),
        HOTBAR_PREV("Hotbar: previous slot"),
        FULLBRIGHT("Toggle Fullbright"),
        FREECAM("Toggle Freecam"),
        LOCATOR("Toggle Locator Arrow"),
        HUD("Toggle HUD"),
        MODULE_HUD("Toggle Module HUD"),
        AUTO_TOTEM("Toggle Auto Totem"),
        AUTO_EAT("Toggle Auto Eat"),
        CRITICALS("Toggle Criticals"),
        KILL_AURA("Toggle Kill Aura"),
        FLIGHT("Toggle Flight"),
        NO_DAMAGE("Toggle No Damage"),
        XRAY("Toggle Xray");

        public final String label;

        ScrollAction(String label) {
            this.label = label;
        }

        /** Cloth Config's enum selector uses toString() for the button text. */
        @Override
        public String toString() {
            return label;
        }
    }

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
    public ScrollAction scrollUpAction = ScrollAction.NONE;
    public ScrollAction scrollDownAction = ScrollAction.NONE;

    // --- Utility (see PvpKitClient javadoc for how each one works) ---
    public boolean autoTotem = false;
    public boolean autoEat = false;
    public int autoEatHungerThreshold = 14;
    public boolean criticals = false;
    public boolean moduleHud = true;

    // --- Xray (see PvpKitClient javadoc / README Fair use) ---
    public boolean xrayEnabled = false;
    public boolean xrayCoal = true;
    public boolean xrayIron = true;
    public boolean xrayCopper = true;
    public boolean xrayGold = true;
    public boolean xrayRedstone = true;
    public boolean xrayLapis = true;
    public boolean xrayEmerald = true;
    public boolean xrayDiamond = true;
    public boolean xrayAncientDebris = true;
    public boolean xrayNetherQuartz = true;
    public boolean xrayContainers = false;

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
        if (instance.scrollUpAction == null) instance.scrollUpAction = ScrollAction.NONE;
        if (instance.scrollDownAction == null) instance.scrollDownAction = ScrollAction.NONE;
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception ignored) {
        }
    }
}
