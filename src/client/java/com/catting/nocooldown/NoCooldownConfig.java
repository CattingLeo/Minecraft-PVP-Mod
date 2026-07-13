package com.catting.nocooldown;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Config for No Cooldown. One setting: the mode, cycling
 * Disabled -> No spear cooldown -> No cooldown.
 * Persisted to config/nocooldown.json.
 */
public class NoCooldownConfig {

    public enum Mode {
        DISABLED("Disabled"),
        NO_SPEAR_COOLDOWN("No spear cooldown"),
        NO_COOLDOWN("No cooldown");

        public final String label;

        Mode(String label) {
            this.label = label;
        }

        /** Cloth Config's enum selector uses toString() for the button text. */
        @Override
        public String toString() {
            return label;
        }
    }

    /** Who Kill Aura is allowed to swing at. */
    public enum KillAuraTargets {
        PLAYERS("Players"),
        HOSTILE_MOBS("Hostile mobs"),
        ALL_MOBS("All mobs + players");

        public final String label;

        KillAuraTargets(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public Mode mode = Mode.DISABLED;

    // --- Extras (independent toggles, not part of the mode cycle) ---
    public boolean unlimitedDurability = false;
    public boolean instantUse = false;
    public boolean noDamage = false;
    public boolean flightEnabled = false;
    public boolean infiniteHunger = false;
    public boolean killAura = false;
    public int killAuraRange = 3;
    public KillAuraTargets killAuraTargets = KillAuraTargets.PLAYERS;

    // ------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NoCooldownConfig instance;

    public static NoCooldownConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nocooldown.json");
    }

    public static void load() {
        try {
            Path p = file();
            if (Files.exists(p)) {
                instance = GSON.fromJson(Files.readString(p), NoCooldownConfig.class);
            }
        } catch (Exception ignored) {
        }
        if (instance == null) {
            instance = new NoCooldownConfig();
        }
        if (instance.mode == null) {
            instance.mode = Mode.DISABLED;
        }
        if (instance.killAuraTargets == null) {
            instance.killAuraTargets = KillAuraTargets.PLAYERS;
        }
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception ignored) {
        }
    }
}
