package com.catting.pvpkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Right Shift opens this: a client-style module GUI -- one small panel per category laid out
 * ACROSS the screen, each one independently draggable and collapsible, plus a Search panel.
 * Click a module to toggle it. Doesn't pause the game.
 *
 * Deliberately NOT one tall column: the panels are separate windows you arrange yourself
 * (Meteor/LiquidBounce style), and their positions persist in config/pvpkit-modulehud.json via
 * ModuleHudLayout. Sub-settings (sliders, HUD position, per-ore Xray, enum pickers) stay in
 * Mod Menu -> .PVP KIT; what belongs here is defined by ModuleRegistry.
 *
 * RENDERING NOTES for this MC version, all verified via javap -- guessing here is what caused
 * previous compile errors in this project:
 *   - Screen has no render() to override; drawing goes through extractRenderState(...), and
 *     Screen's own implementation ONLY iterates the widget list (checked in its bytecode), so
 *     panel chrome is drawn first and super.extractRenderState() puts the widgets on top.
 *   - Rows are AbstractWidget subclasses, so Screen handles click dispatch, hover, focus and
 *     the drag routing (AbstractContainerEventHandler forwards drags to the pressed child).
 *   - Minecraft has no public screen field/setScreen; opening and closing goes through
 *     Minecraft#gui.screen()/setScreen(Screen) -- see PvpKitKeybinds.
 *
 * Dragging moves widgets in place with setX/setY rather than rebuilding: rebuilding mid-drag
 * would replace the very widget the drag is being routed to and the panel would stick.
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
    static final int PAD = 5;
    static final int ROW_INDENT = 13;
    private static final int PANEL_W = 116;
    private static final int ROW_H = 13;
    private static final int HEADER_H = 15;
    private static final int GAP = 5;
    private static final int MARGIN = 5;

    private static final String SEARCH_KEY = "search";

    private static String search = "";

    /** Panel key -> the widgets that move with it, so a drag can shift the whole panel. */
    private final Map<String, List<AbstractWidget>> panelWidgets = new LinkedHashMap<>();
    /** Panel key -> {x, y, width, height} for the chrome drawn behind those widgets. */
    private final Map<String, int[]> panelBounds = new LinkedHashMap<>();

    private EditBox searchBox;

    /**
     * Rebuilds are deferred to the next tick rather than run inline. Both triggers (the search
     * responder and a header toggle) fire from inside event dispatch, which is iterating the
     * very widget list rebuildWidgets() clears -- doing it inline risks mutating that list
     * mid-iteration.
     */
    private boolean rebuildQueued;

    /**
     * Sub-pixel drag carry. Drag deltas arrive as doubles and a slow drag is a stream of values
     * below 1, so rounding each one on its own would floor them all to zero and the panel would
     * refuse to move until you yanked it.
     */
    private String draggingKey;
    private double residualX;
    private double residualY;

    public ModuleListScreen() {
        super(Component.literal("Modules"));
    }

    @Override
    public void tick() {
        super.tick();
        if (rebuildQueued) {
            rebuildQueued = false;
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        panelWidgets.clear();
        panelBounds.clear();

        placeDefaults();
        buildSearchPanel();
        for (ModuleRegistry.Category category : ModuleRegistry.Category.values()) {
            buildCategoryPanel(category);
        }
    }

    /**
     * First-run placement: the panels flow left to right across the top and wrap when they'd
     * run off the edge, so the default really is a horizontal bar rather than a column. Only
     * applied to panels the user has never dragged (placed=false).
     */
    private void placeDefaults() {
        int x = MARGIN;
        int y = MARGIN;
        List<String> keys = new ArrayList<>();
        keys.add(SEARCH_KEY);
        for (ModuleRegistry.Category category : ModuleRegistry.Category.values()) keys.add(category.name());

        for (String key : keys) {
            ModuleHudLayout.Panel panel = ModuleHudLayout.panel(key);
            if (x + PANEL_W > this.width - MARGIN && x > MARGIN) {
                x = MARGIN;
                y += HEADER_H + GAP;
            }
            if (!panel.placed) {
                panel.x = x;
                panel.y = y;
                panel.placed = true;
            }
            x += PANEL_W + GAP;
        }
    }

    private void buildSearchPanel() {
        ModuleHudLayout.Panel panel = clamped(SEARCH_KEY);
        List<AbstractWidget> widgets = new ArrayList<>();

        widgets.add(addRenderableWidget(header(SEARCH_KEY, panel, "Search",
                () -> enabledCount() + "/" + ModuleRegistry.all().size(), false)));

        int height = HEADER_H;
        if (panel.expanded) {
            searchBox = new EditBox(this.font, panel.x + PAD, panel.y + HEADER_H + 2,
                    PANEL_W - PAD * 2, ROW_H, Component.literal("Search"));
            searchBox.setHint(Component.literal("Search..."));
            searchBox.setMaxLength(32);
            searchBox.setBordered(false);
            searchBox.setTextColor(TEXT);
            searchBox.setValue(search);
            searchBox.setResponder(value -> {
                if (value.equals(search)) return;
                search = value;
                rebuildQueued = true;
            });
            widgets.add(addRenderableWidget(searchBox));
            // Focused on open so you can just type. EditBox doesn't consume Escape, so Escape
            // still closes the screen (same as the creative inventory's search field).
            setFocused(searchBox);
            searchBox.setFocused(true);
            height += ROW_H + 4;
        }

        panelWidgets.put(SEARCH_KEY, widgets);
        panelBounds.put(SEARCH_KEY, new int[]{panel.x, panel.y, PANEL_W, height});
    }

    private void buildCategoryPanel(ModuleRegistry.Category category) {
        List<ModuleRegistry.Module> matches = ModuleRegistry.inCategory(category, search);
        if (matches.isEmpty()) return; // nothing to show for the current filter

        String key = category.name();
        ModuleHudLayout.Panel panel = clamped(key);
        List<AbstractWidget> widgets = new ArrayList<>();

        boolean open = panel.expanded || !search.isBlank();
        String count = String.valueOf(matches.size());
        widgets.add(addRenderableWidget(header(key, panel, category.label, () -> count, true)));

        int height = HEADER_H;
        if (open) {
            int y = panel.y + HEADER_H;
            for (ModuleRegistry.Module module : matches) {
                widgets.add(addRenderableWidget(new ModuleRowWidget(panel.x, y, PANEL_W, ROW_H, module)));
                y += ROW_H;
            }
            height += matches.size() * ROW_H;
        }

        panelWidgets.put(key, widgets);
        panelBounds.put(key, new int[]{panel.x, panel.y, PANEL_W, height});
    }

    private ModuleCategoryWidget header(String key, ModuleHudLayout.Panel panel, String title,
                                        java.util.function.Supplier<String> trailing, boolean showArrow) {
        boolean open = panel.expanded || (showArrow && !search.isBlank());
        return new ModuleCategoryWidget(panel.x, panel.y, PANEL_W, HEADER_H, title, trailing,
                open, showArrow,
                () -> {
                    panel.expanded = !panel.expanded;
                    ModuleHudLayout.save();
                    rebuildQueued = true;
                },
                (dx, dy) -> movePanel(key, panel, dx, dy),
                ModuleHudLayout::save);
    }

    /** Shifts a panel and everything in it, keeping it on screen. */
    private void movePanel(String key, ModuleHudLayout.Panel panel, double dx, double dy) {
        int[] bounds = panelBounds.get(key);
        if (bounds == null) return;

        if (!key.equals(draggingKey)) { // a new drag starts with no leftover carry
            draggingKey = key;
            residualX = 0.0;
            residualY = 0.0;
        }
        residualX += dx;
        residualY += dy;
        int stepX = (int) residualX;
        int stepY = (int) residualY;
        residualX -= stepX;
        residualY -= stepY;

        int nextX = Mth.clamp(panel.x + stepX, 0, Math.max(0, this.width - PANEL_W));
        int nextY = Mth.clamp(panel.y + stepY, 0, Math.max(0, this.height - HEADER_H));
        int shiftX = nextX - panel.x;
        int shiftY = nextY - panel.y;
        if (shiftX == 0 && shiftY == 0) return;

        panel.x = nextX;
        panel.y = nextY;
        bounds[0] = nextX;
        bounds[1] = nextY;
        for (AbstractWidget widget : panelWidgets.getOrDefault(key, List.of())) {
            widget.setX(widget.getX() + shiftX);
            widget.setY(widget.getY() + shiftY);
        }
    }

    /** Pulls a stored panel back on screen -- a saved layout can outlive the window size it was made at. */
    private ModuleHudLayout.Panel clamped(String key) {
        ModuleHudLayout.Panel panel = ModuleHudLayout.panel(key);
        panel.x = Mth.clamp(panel.x, 0, Math.max(0, this.width - PANEL_W));
        panel.y = Mth.clamp(panel.y, 0, Math.max(0, this.height - HEADER_H));
        return panel;
    }

    private static int enabledCount() {
        int enabled = 0;
        for (ModuleRegistry.Module module : ModuleRegistry.all()) {
            if (module.active()) enabled++;
        }
        return enabled;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        for (int[] bounds : panelBounds.values()) {
            int x = bounds[0];
            int y = bounds[1];
            int right = x + bounds[2];
            int bottom = y + bounds[3];
            graphics.fill(x, y, right, bottom, PANEL_BG);
            graphics.outline(x, y, bounds[2], bounds[3], PANEL_EDGE);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
    }

    @Override
    public void onClose() {
        ModuleHudLayout.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
