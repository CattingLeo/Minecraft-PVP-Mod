package com.catting.pvpkit;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Right Shift opens this: a client-style module panel in the top-right corner -- a search
 * box over collapsible Combat / Render / Player / Movement / Misc sections, one click per
 * module to toggle it. Sub-settings (sliders, positions, per-ore Xray, enum pickers) stay in
 * Mod Menu -> .PVP KIT, which is the screen built for them; what belongs here is defined by
 * ModuleRegistry.
 *
 * Doesn't pause the game (isPauseScreen() false) and shows the world behind it
 * (extractTransparentBackground) instead of the usual dark/blurred menu background, so it
 * reads as a lightweight overlay rather than a full pause screen.
 *
 * RENDERING NOTES for this MC version, all verified via javap -- guessing here is what caused
 * previous compile errors in this project:
 *   - Screen has no render() to override; drawing goes through extractRenderState(...), and
 *     Screen's own implementation ONLY iterates the widget list (checked in its bytecode). So
 *     panel chrome is drawn first and super.extractRenderState() puts the widgets on top.
 *   - Rows are AbstractWidget subclasses (ModuleRowWidget / ModuleCategoryWidget) rather than
 *     hand-rolled hit-testing, so Screen handles click dispatch and hover for free.
 *   - Minecraft has no public screen field/setScreen; opening and closing goes through
 *     Minecraft#gui.screen()/setScreen(Screen) -- see PvpKitKeybinds.
 *
 * Search and expansion state are static so they survive closing and reopening the panel:
 * re-collapsing every section on each open would make the whole thing tedious in a fight.
 */
public class ModuleListScreen extends Screen {

    // --- palette (ARGB) ---
    static final int ACCENT = 0xFF9B6BFF;
    static final int PANEL_BG = 0xE0121216;
    static final int PANEL_EDGE = 0xFF2A2A33;
    static final int HEADER_BG = 0xFF1B1B22;
    static final int HEADER_BG_HOVER = 0xFF24242E;
    static final int ROW_HOVER = 0x28FFFFFF;
    static final int TEXT = 0xFFE8E8F0;
    static final int TEXT_DIM = 0xFF9A9AA8;
    static final int TEXT_FAINT = 0xFF6A6A78;

    // --- metrics ---
    static final int PAD = 6;
    static final int ROW_INDENT = 14;
    private static final int PANEL_W = 174;
    private static final int ROW_H = 14;
    private static final int TITLE_H = 18;
    private static final int SEARCH_H = 14;
    private static final int MARGIN = 6;

    /** Kept across opens -- see class javadoc. */
    private static final Map<ModuleRegistry.Category, Boolean> EXPANDED =
            new EnumMap<>(ModuleRegistry.Category.class);
    private static String search = "";
    private static int scroll;

    private EditBox searchBox;
    private int panelX;
    private int panelHeight;
    private int listTop;
    private int listHeight;

    public ModuleListScreen() {
        super(Component.literal("Modules"));
    }

    private static boolean expanded(ModuleRegistry.Category category) {
        return EXPANDED.getOrDefault(category, Boolean.FALSE);
    }

    @Override
    protected void init() {
        panelX = this.width - PANEL_W - MARGIN;
        listTop = MARGIN + TITLE_H + SEARCH_H + PAD;

        searchBox = new EditBox(this.font, panelX + PAD, MARGIN + TITLE_H,
                PANEL_W - PAD * 2, SEARCH_H, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search..."));
        searchBox.setMaxLength(32);
        searchBox.setBordered(false);
        searchBox.setTextColor(TEXT);
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            if (value.equals(search)) return;
            search = value;
            scroll = 0; // a new filter with the old offset can land you past the end of the list
            rebuildWidgets();
        });
        addRenderableWidget(searchBox);
        // Focused on open so you can just type, the way a client's search behaves. EditBox
        // doesn't consume Escape, so Escape still closes the panel (same as the creative
        // inventory's search field).
        setFocused(searchBox);
        searchBox.setFocused(true);

        // Lay the sections out into a flat row list first, so culling and scrolling only ever
        // deal with "row N of M" rather than the section structure.
        List<Runnable> rows = new java.util.ArrayList<>();
        boolean searching = !search.isBlank();
        for (ModuleRegistry.Category category : ModuleRegistry.Category.values()) {
            List<ModuleRegistry.Module> matches = ModuleRegistry.inCategory(category, search);
            if (matches.isEmpty()) continue; // hide sections with nothing to show while searching

            final int index = rows.size();
            final boolean open = searching || expanded(category);
            rows.add(placeholder(index, y -> addRenderableWidget(new ModuleCategoryWidget(
                    panelX, y, PANEL_W, ROW_H, category, matches.size(), open, () -> {
                        EXPANDED.put(category, !expanded(category));
                        rebuildWidgets();
                    }))));

            if (!open) continue;
            for (ModuleRegistry.Module module : matches) {
                final int rowIndex = rows.size();
                rows.add(placeholder(rowIndex, y -> addRenderableWidget(
                        new ModuleRowWidget(panelX, y, PANEL_W, ROW_H, module))));
            }
        }

        int contentHeight = rows.size() * ROW_H;
        int available = this.height - listTop - MARGIN;
        // An empty result still needs a row's worth of space for the "No modules match" line.
        listHeight = contentHeight == 0 ? ROW_H : Math.min(contentHeight, Math.max(ROW_H, available));
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - listHeight));
        panelHeight = listTop - MARGIN + listHeight + PAD;

        // Only build widgets that are actually on screen. That's what keeps scrolling correct
        // without a scissor: clipping the widget pass would clip the search box with it, since
        // Screen draws every widget in one go.
        for (int i = 0; i < rows.size(); i++) {
            int y = listTop - scroll + i * ROW_H;
            if (y + ROW_H <= listTop || y >= listTop + listHeight) continue;
            rows.get(i).run();
        }
    }

    private interface RowPlacer {
        void place(int y);
    }

    /** Defers one row's widget creation until its final Y is known (after scroll is clamped). */
    private Runnable placeholder(int index, RowPlacer placer) {
        return () -> placer.place(listTop - scroll + index * ROW_H);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int contentHeight = totalRows() * ROW_H;
        if (contentHeight > listHeight) {
            int max = contentHeight - listHeight;
            int next = Mth.clamp(scroll - (int) (deltaY * ROW_H), 0, max);
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private static int enabledCount() {
        int enabled = 0;
        for (ModuleRegistry.Module module : ModuleRegistry.all()) {
            if (module.active()) enabled++;
        }
        return enabled;
    }

    /** Row count for the current filter/expansion -- headers plus the modules of open sections. */
    private int totalRows() {
        int rows = 0;
        boolean searching = !search.isBlank();
        for (ModuleRegistry.Category category : ModuleRegistry.Category.values()) {
            List<ModuleRegistry.Module> matches = ModuleRegistry.inCategory(category, search);
            if (matches.isEmpty()) continue;
            rows++;
            if (searching || expanded(category)) rows += matches.size();
        }
        return rows;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int top = MARGIN;
        int right = panelX + PANEL_W;
        int bottom = top + panelHeight;

        graphics.fill(panelX, top, right, bottom, PANEL_BG);
        graphics.outline(panelX, top, PANEL_W, panelHeight, PANEL_EDGE);
        graphics.fill(panelX, top, right, top + 2, ACCENT); // accent strip along the top

        graphics.text(this.font, "PVP KIT", panelX + PAD, top + 7, TEXT, false);
        // Enabled/total, so the panel is worth a glance even with every section collapsed.
        String tally = enabledCount() + "/" + ModuleRegistry.all().size();
        graphics.text(this.font, tally, right - PAD - this.font.width(tally), top + 7, TEXT_FAINT, false);
        graphics.fill(panelX + PAD, MARGIN + TITLE_H + SEARCH_H - 1,
                right - PAD, MARGIN + TITLE_H + SEARCH_H, PANEL_EDGE); // underline under the search box

        if (totalRows() == 0) {
            graphics.text(this.font, "No modules match", panelX + ROW_INDENT, listTop + 4, TEXT_FAINT, false);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
