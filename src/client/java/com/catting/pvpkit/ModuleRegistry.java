package com.catting.pvpkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import com.catting.nocooldown.NoCooldownConfig;

/**
 * The catalogue behind the module HUD: every toggle the overlay exposes, grouped into the
 * categories it renders as collapsible sections.
 *
 * Kept separate from ModuleListScreen so that screen stays purely about layout and drawing --
 * adding a module here makes it appear in the HUD (and in search) with no UI changes at all.
 *
 * Deliberately NOT a mirror of the config files. Only things that behave like a module -- an
 * on/off you'd flip mid-game -- belong here; the numeric sliders, positions, per-ore Xray
 * checkboxes and enum pickers stay in Mod Menu -> .PVP KIT, which is the screen built for them.
 */
public final class ModuleRegistry {

    public enum Category {
        COMBAT("Combat"),
        RENDER("Render"),
        PLAYER("Player"),
        MOVEMENT("Movement"),
        MISC("Misc");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    /** One clickable row in the HUD. */
    public interface Module {
        String name();

        Category category();

        /** Right-hand state text ("ON"/"OFF", or the mode name for a cycling row). */
        String state();

        /** Drawn in the accent colour with an active marker when true. */
        boolean active();

        /** What a left click does. */
        void activate();
    }

    private ModuleRegistry() {
    }

    // ---- the catalogue -------------------------------------------------------------

    private static List<Module> modules;

    /** Built once, lazily -- the lambdas read live config, so the list itself never goes stale. */
    public static List<Module> all() {
        if (modules != null) return modules;

        List<Module> m = new ArrayList<>();
        PvpKitConfig c = PvpKitConfig.get();
        NoCooldownConfig nc = NoCooldownConfig.get();

        // --- Combat ---
        m.add(nc("Kill Aura", Category.COMBAT, () -> nc.killAura, v -> nc.killAura = v));
        m.add(kit("Auto Totem", Category.COMBAT, () -> c.autoTotem, v -> c.autoTotem = v));
        m.add(cooldownMode());
        m.add(kit("Cooldown Flash", Category.COMBAT, () -> c.cooldownFlash, v -> c.cooldownFlash = v));
        m.add(kit("Hit Marker", Category.COMBAT, () -> c.showHitMarker, v -> c.showHitMarker = v));
        m.add(kit("No Crystal Explosion", Category.COMBAT, () -> c.noCrystalExplosion, v -> c.noCrystalExplosion = v));
        m.add(kit("Hotbar Swap Flash", Category.COMBAT, () -> c.hotbarSwapFlash, v -> c.hotbarSwapFlash = v));

        // --- Render ---
        m.add(kit("Fullbright", Category.RENDER, () -> c.fullbright, v -> c.fullbright = v));
        m.add(kit("Xray", Category.RENDER, () -> c.xrayEnabled, v -> c.xrayEnabled = v));
        m.add(kit("No Nausea", Category.RENDER, () -> c.noNausea, v -> c.noNausea = v));
        m.add(kit("No Hurt Tilt", Category.RENDER, () -> c.noHurtTilt, v -> c.noHurtTilt = v));
        m.add(kit("No Darkness", Category.RENDER, () -> c.noDarkness, v -> c.noDarkness = v));
        m.add(kit("No Blindness", Category.RENDER, () -> c.noBlindness, v -> c.noBlindness = v));
        m.add(kit("No Slowness FOV", Category.RENDER, () -> c.noSlownessFov, v -> c.noSlownessFov = v));
        m.add(kit("No Speed FOV", Category.RENDER, () -> c.noSpeedFov, v -> c.noSpeedFov = v));

        // --- Player ---
        m.add(kit("Auto Eat", Category.PLAYER, () -> c.autoEat, v -> c.autoEat = v));
        m.add(nc("Infinite Hunger", Category.PLAYER, () -> nc.infiniteHunger, v -> nc.infiniteHunger = v));
        m.add(nc("No Damage", Category.PLAYER, () -> nc.noDamage, v -> nc.noDamage = v));
        m.add(nc("Unlimited Durability", Category.PLAYER, () -> nc.unlimitedDurability, v -> nc.unlimitedDurability = v));
        m.add(nc("Instant Use", Category.PLAYER, () -> nc.instantUse, v -> nc.instantUse = v));

        // --- Movement ---
        m.add(nc("Flight", Category.MOVEMENT, () -> nc.flightEnabled, v -> nc.flightEnabled = v));
        m.add(freecam());

        // --- Misc ---
        m.add(kit("Show FPS", Category.MISC, () -> c.showFps, v -> c.showFps = v));
        m.add(kit("Show CPS", Category.MISC, () -> c.showCps, v -> c.showCps = v));
        m.add(kit("Show Ping", Category.MISC, () -> c.showPing, v -> c.showPing = v));
        m.add(kit("Rainbow Text", Category.MISC, () -> c.rainbowText, v -> c.rainbowText = v));
        m.add(kit("Totem Pop Counter", Category.MISC, () -> c.totemPop, v -> c.totemPop = v));
        m.add(kit("Totem Flash", Category.MISC, () -> c.totemFlash, v -> c.totemFlash = v));
        m.add(kit("Hide Center Totem", Category.MISC, () -> c.hideCenterTotem, v -> c.hideCenterTotem = v));
        m.add(kit("Disable Scroll Hotbar", Category.MISC, () -> c.disableScrollHotbar, v -> c.disableScrollHotbar = v));

        modules = List.copyOf(m);
        return modules;
    }

    /** Modules in one category, filtered by the search box (blank search matches everything). */
    public static List<Module> inCategory(Category category, String search) {
        List<Module> out = new ArrayList<>();
        for (Module m : all()) {
            if (m.category() == category && matches(m, search)) out.add(m);
        }
        return out;
    }

    /** Case-insensitive substring match on the module name, so "tot" finds Auto Totem. */
    public static boolean matches(Module module, String search) {
        if (search == null || search.isBlank()) return true;
        return module.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    // ---- factories -----------------------------------------------------------------

    private interface BoolSetter {
        void set(boolean value);
    }

    /** A PvP Kit toggle. Saves AND re-applies, since several of these drive live render state. */
    private static Module kit(String name, Category category, BooleanSupplier getter, BoolSetter setter) {
        return simple(name, category, getter, value -> {
            setter.set(value);
            PvpKitConfig.save();
            PvpKitClient.applyConfig();
        });
    }

    /** A No Cooldown toggle -- different config file, so a different save. */
    private static Module nc(String name, Category category, BooleanSupplier getter, BoolSetter setter) {
        return simple(name, category, getter, value -> {
            setter.set(value);
            NoCooldownConfig.save();
        });
    }

    private static Module simple(String name, Category category, BooleanSupplier getter, BoolSetter setter) {
        return new Module() {
            @Override public String name() { return name; }
            @Override public Category category() { return category; }
            @Override public String state() { return getter.getAsBoolean() ? "ON" : "OFF"; }
            @Override public boolean active() { return getter.getAsBoolean(); }
            @Override public void activate() { setter.set(!getter.getAsBoolean()); }
        };
    }

    /** Freecam lives in its own manager rather than a config flag -- it's session state, not a setting. */
    private static Module freecam() {
        return new Module() {
            @Override public String name() { return "Freecam"; }
            @Override public Category category() { return Category.MOVEMENT; }
            @Override public String state() { return FreecamManager.isActive() ? "ON" : "OFF"; }
            @Override public boolean active() { return FreecamManager.isActive(); }
            @Override public void activate() { FreecamManager.toggle(); }
        };
    }

    /** No Cooldown is a 3-way cycle, not an on/off, so its row shows the mode name instead of ON/OFF. */
    private static Module cooldownMode() {
        return new Module() {
            @Override public String name() { return "No Cooldown"; }
            @Override public Category category() { return Category.COMBAT; }
            @Override public String state() { return NoCooldownConfig.get().mode.label; }

            @Override
            public boolean active() {
                return NoCooldownConfig.get().mode != NoCooldownConfig.Mode.DISABLED;
            }

            @Override
            public void activate() {
                NoCooldownConfig config = NoCooldownConfig.get();
                NoCooldownConfig.Mode[] values = NoCooldownConfig.Mode.values();
                config.mode = values[(config.mode.ordinal() + 1) % values.length];
                NoCooldownConfig.save();
            }
        };
    }
}
