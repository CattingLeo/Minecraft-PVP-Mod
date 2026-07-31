package com.catting.pvpkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Ordered multi-binding: several actions on one key, fired top-to-bottom in
 * the order they're listed.
 *
 * Exists because the separate Multi Key Bindings mod stores its bindings in
 * its own config and fires them in no guaranteed order, and being a third-party
 * mod there's no way for this one to impose an order on it. Slots here are a
 * fixed, numbered list precisely so "the order you added them" is explicit and
 * visible rather than implied by a hash map's iteration order.
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

    /** One row: an action plus the key that triggers it. Key is stored as InputConstants' own name ("key.keyboard.left.alt"). */
    public static class Slot {
        public MultiAction action = MultiAction.NONE;
        public String key = "key.keyboard.unknown";
        /**
         * When this binding was added, which is what firing order follows -- NOT the
         * row number. Set once when the row goes from None to a real action, and
         * cleared back to -1 when it's set back to None, so re-using an old row puts
         * it at the END of the order rather than back where it used to be.
         */
        public int addedSeq = -1;
    }

    /** Next insertion number, i.e. one past the highest currently in use. */
    public static int nextSeq() {
        int max = -1;
        for (Slot s : get().slots) max = Math.max(max, s.addedSeq);
        return max + 1;
    }

    public static final int SLOT_COUNT = 10;

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
        // Pad to a stable length so slot N is always the same row in the UI and
        // the firing order can't shift just because an earlier slot was cleared.
        while (instance.slots.size() < SLOT_COUNT) instance.slots.add(new Slot());
        for (Slot s : instance.slots) {
            if (s.action == null) s.action = MultiAction.NONE;
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
