package com.catting.pvpkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Where each module-HUD panel sits and whether it's open, persisted to
 * config/pvpkit-modulehud.json.
 *
 * Separate from PvpKitConfig on purpose: this is window furniture the user arranges by
 * dragging, not a setting anyone edits in Mod Menu, and it gets written on every drag release.
 * Keeping it out of the main config avoids rewriting that file for pure UI noise.
 */
public class ModuleHudLayout {

    /** One panel's placement. Mutable and written in place while dragging. */
    public static class Panel {
        public int x;
        public int y;
        public boolean expanded;

        /** Gson needs this; also the "never been placed" marker via placed=false. */
        public boolean placed;
    }

    public Map<String, Panel> panels = new HashMap<>();

    // ------------------------------------------------------------------
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModuleHudLayout instance;

    public static ModuleHudLayout get() {
        if (instance == null) load();
        return instance;
    }

    /** The stored panel for a key, created unplaced on first use so the screen can lay it out. */
    public static Panel panel(String key) {
        ModuleHudLayout layout = get();
        Panel panel = layout.panels.get(key);
        if (panel == null) {
            panel = new Panel();
            panel.expanded = true;
            layout.panels.put(key, panel);
        }
        return panel;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("pvpkit-modulehud.json");
    }

    public static void load() {
        try {
            Path p = file();
            if (Files.exists(p)) instance = GSON.fromJson(Files.readString(p), ModuleHudLayout.class);
        } catch (Exception ignored) {
        }
        if (instance == null) instance = new ModuleHudLayout();
        if (instance.panels == null) instance.panels = new HashMap<>();
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception ignored) {
        }
    }
}
