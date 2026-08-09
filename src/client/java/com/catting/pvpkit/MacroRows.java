package com.catting.pvpkit;

import java.util.ArrayList;
import java.util.List;

import com.catting.pvpkit.MacroConfig.Step;
import com.catting.pvpkit.mixin.KeyEntryAccessor;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;

/**
 * Shared state for the macro rows that this mod adds to the REAL vanilla Key Binds
 * screen. Static because the two mixins that need it (KeyEntryMixin, which draws the
 * "+" on every vanilla row, and KeyBindsListMixin, which splices the duplicate rows
 * into the list) have no clean way to hold instance state of their own -- and only
 * one Key Binds screen can exist at a time, so a single current-list reference is
 * enough.
 */
public final class MacroRows {

    public enum Tab {
        ONE_TICK("One Tick"),
        STEP_BY_STEP("Step by Step");

        public final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static Tab activeTab = Tab.ONE_TICK;
    private static KeyBindsList currentList;

    // Column geometry, published by KeyEntryMixin from whatever vanilla actually laid
    // out this frame, so MacroRowEntry lines its trash/key/Reset buttons up with the
    // real rows instead of guessing at coordinates.
    private static int plusX = -1;
    private static int plusW = 20;
    private static int keyX = -1;
    private static int keyW = 75;

    private MacroRows() {
    }

    public static void publishColumns(int plusX, int plusW, int keyX, int keyW) {
        MacroRows.plusX = plusX;
        MacroRows.plusW = plusW;
        MacroRows.keyX = keyX;
        MacroRows.keyW = keyW;
    }

    public static int plusX() {
        return plusX;
    }

    public static int plusW() {
        return plusW;
    }

    public static int keyX() {
        return keyX;
    }

    public static int keyW() {
        return keyW;
    }

    public static Tab activeTab() {
        return activeTab;
    }

    public static void setActiveTab(Tab tab) {
        activeTab = tab;
        rebuild();
    }

    /** Steps belonging to whichever tab is currently selected. */
    public static List<Step> steps() {
        MacroConfig cfg = MacroConfig.get();
        return activeTab == Tab.ONE_TICK ? cfg.oneTick : cfg.stepByStep;
    }

    /** Steps in the active tab that duplicate the given vanilla keybind, in play order. */
    public static List<Step> stepsFor(KeyMapping mapping) {
        List<Step> out = new ArrayList<>();
        for (Step s : steps()) {
            if (mapping.getName().equals(s.keyName)) out.add(s);
        }
        return out;
    }

    /** Called by the "+" on a vanilla row: appends another duplicate of that keybind to the active tab. */
    public static void addStep(KeyMapping mapping) {
        Step s = new Step();
        s.keyName = mapping.getName();
        steps().add(s);
        MacroConfig.save();
        MacroBindings.sync(); // (re)register the extra keys into vanilla's dispatch map
        rebuild();
    }

    public static void removeStep(Step step) {
        steps().remove(step);
        MacroConfig.save();
        MacroBindings.sync(); // (re)register the extra keys into vanilla's dispatch map
        rebuild();
    }

    public static void setCurrentList(KeyBindsList list) {
        currentList = list;
    }

    /**
     * Rebuilds the Key Binds list so each duplicate row sits directly under the vanilla
     * row it mirrors. Walks the list's CURRENT entries, keeps every vanilla entry exactly
     * as-is (category headers included -- nothing about vanilla's own rows is recreated
     * or restyled), and splices a MacroRowEntry in after each keybind row that has steps
     * in the active tab.
     *
     * Entries that are already MacroRowEntry are dropped first, so this is idempotent and
     * safe to call on every add/remove/tab switch.
     */
    @SuppressWarnings("unchecked")
    public static void rebuild() {
        KeyBindsList list = currentList;
        if (list == null) return;

        AbstractSelectionList<KeyBindsList.Entry> asList = (AbstractSelectionList<KeyBindsList.Entry>) (Object) list;
        List<KeyBindsList.Entry> rebuilt = new ArrayList<>();
        for (KeyBindsList.Entry entry : new ArrayList<>(asList.children())) {
            if (entry instanceof MacroRowEntry) continue; // stale rows from the previous build
            rebuilt.add(entry);
            if (!(entry instanceof KeyEntryAccessor accessor)) continue;
            KeyMapping mapping = accessor.pvpkit$key();
            if (mapping == null) continue;
            for (Step step : stepsFor(mapping)) {
                rebuilt.add(new MacroRowEntry(mapping, step));
            }
        }
        asList.replaceEntries(rebuilt);
    }
}
