package com.catting.pvpkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Ordered multi-binding: several actions can share one key, and several keys
 * can drive one action, fired in the order each binding was ADDED (see
 * addedSeq) rather than list/row order.
 *
 * Exists because the separate Multi Key Bindings mod stores its bindings in
 * its own config and fires them in no guaranteed order, and being a
 * third-party mod there's no way for this one to impose an order on it.
 *
 * The Mod Menu screen shows one row per action (every possible MultiAction),
 * one keycode field per key currently bound to it, plus one always-present
 * empty field at the end for adding another. Setting that empty field to a
 * real key appends a brand-new Slot for that action -- see PvpKitModMenu.
 * A fresh "add another" row for that same action only appears the next time
 * the screen is opened (Cloth Config builds its entry list once, from
 * whatever's in `slots` at screen-open time), which is an accepted trade-off
 * for not needing a fully custom dynamic-list Screen.
 *
 * config/multibind.json
 */
public class MultiBindConfig {

    /** What one slot does when its key fires. Vanilla actions are driven by holding the real KeyMapping down. */
    public enum MultiAction {
        NONE("None"),
        HOTBAR_1("Hotbar slot 1"),
        HOTBAR_2("Hotbar slot 2"),
        HOTBAR_3("Hotbar slot 3"),
        HOTBAR_4("Hotbar slot 4"),
        HOTBAR_5("Hotbar slot 5"),
        HOTBAR_6("Hotbar slot 6"),
        HOTBAR_7("Hotbar slot 7"),
        HOTBAR_8("Hotbar slot 8"),
        HOTBAR_9("Hotbar slot 9"),
        ATTACK("Attack"),
        USE("Use / place"),
        SWAP_OFFHAND("Swap offhand"),
        DROP("Drop item"),
        JUMP("Jump"),
        SNEAK("Sneak"),
        SPRINT("Sprint"),
        TOGGLE_FULLBRIGHT("Toggle Fullbright"),
        TOGGLE_FREECAM("Toggle Freecam"),
        TOGGLE_KILL_AURA("Toggle Kill Aura"),
        TOGGLE_FLIGHT("Toggle Flight");

        public final String label;

        MultiAction(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** One binding: an action plus the key that triggers it. Key is stored as InputConstants' own name ("key.keyboard.left.alt"). */
    public static class Slot {
        public MultiAction action = MultiAction.NONE;
        public String key = "key.keyboard.unknown";
        /**
         * When this binding's key was set, which is what firing order follows -- NOT
         * list position. Stamped once when the key goes from unset to a real key, and
         * cleared back to -1 if it's cleared back to unset, so re-binding it later
         * puts it at the END of the order rather than back where it used to be.
         */
        public int addedSeq = -1;
    }

    /** Next insertion number, i.e. one past the highest currently in use. */
    public static int nextSeq() {
        int max = -1;
        for (Slot s : get().slots) max = Math.max(max, s.addedSeq);
        return max + 1;
    }

    /** Existing bindings for one action, in list order (display order for the "Binding N" rows). */
    public static List<Slot> slotsFor(MultiAction action) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : get().slots) {
            if (s.action == action) out.add(s);
        }
        return out;
    }

    /** Appends a brand-new, still-unbound slot for the given action -- the backing object for an "add another" row. */
    public static Slot addSlot(MultiAction action) {
        Slot s = new Slot();
        s.action = action;
        get().slots.add(s);
        return s;
    }

    public List<Slot> slots = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MultiBindConfig instance;

    public static MultiBindConfig get() {
        if (instance == null) load();
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("multibind.json");
    }

    public static void load() {
        try {
            Path p = file();
            if (Files.exists(p)) instance = GSON.fromJson(Files.readString(p), MultiBindConfig.class);
        } catch (Exception ignored) {
        }
        if (instance == null) instance = new MultiBindConfig();
        if (instance.slots == null) instance.slots = new ArrayList<>();
        instance.slots.removeIf(s -> s.action == null || s.action == MultiAction.NONE);
        for (Slot s : instance.slots) {
            if (s.key == null) s.key = "key.keyboard.unknown";
        }
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception ignored) {
        }
    }
}
