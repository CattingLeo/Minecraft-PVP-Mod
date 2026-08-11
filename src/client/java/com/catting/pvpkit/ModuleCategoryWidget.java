package com.catting.pvpkit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A panel's title bar: drag it to move the whole panel, click it to collapse or expand.
 *
 * Collapse fires on RELEASE rather than on press, and only when the pointer didn't move in
 * between. Toggling on press (the obvious place, and where AbstractWidget#onClick runs) means
 * every drag would also collapse the panel you were trying to move, since a drag necessarily
 * begins with a press on this same widget.
 */
public class ModuleCategoryWidget extends AbstractWidget {

    /** Receives per-frame drag deltas from the title bar. */
    public interface DragHandler {
        void moveBy(double dx, double dy);
    }

    private final String title;
    /**
     * Read every frame, not captured once: the search panel's trailing text is the live
     * enabled/total tally, which changes the instant you click a module in another panel and
     * would otherwise sit stale until something happened to rebuild the widgets.
     */
    private final java.util.function.Supplier<String> trailing;
    private final boolean expanded;
    private final boolean showArrow;
    private final Runnable onToggle;
    private final DragHandler onDrag;
    private final Runnable onDragEnd;

    private boolean dragged;

    public ModuleCategoryWidget(int x, int y, int width, int height, String title,
                                java.util.function.Supplier<String> trailing,
                                boolean expanded, boolean showArrow, Runnable onToggle,
                                DragHandler onDrag, Runnable onDragEnd) {
        super(x, y, width, height, Component.literal(title));
        this.title = title;
        this.trailing = trailing;
        this.expanded = expanded;
        this.showArrow = showArrow;
        this.onToggle = onToggle;
        this.onDrag = onDrag;
        this.onDragEnd = onDragEnd;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int right = x + this.width;

        graphics.fill(x, y, right, y + this.height, isHovered()
                ? ModuleListScreen.HEADER_BG_HOVER
                : ModuleListScreen.HEADER_BG);
        graphics.fill(x, y, right, y + 1, ModuleListScreen.ACCENT);

        int textY = y + (this.height - Minecraft.getInstance().font.lineHeight) / 2 + 1;
        if (showArrow) arrow(graphics, x + ModuleListScreen.PAD, y + this.height / 2);
        graphics.text(Minecraft.getInstance().font, title,
                x + (showArrow ? ModuleListScreen.ROW_INDENT : ModuleListScreen.PAD), textY,
                ModuleListScreen.TEXT, false);

        String trailingText = trailing.get();
        if (!trailingText.isEmpty()) {
            int trailingWidth = Minecraft.getInstance().font.width(trailingText);
            graphics.text(Minecraft.getInstance().font, trailingText,
                    right - ModuleListScreen.PAD - trailingWidth, textY, ModuleListScreen.TEXT_FAINT, false);
        }
    }

    /**
     * The expand/collapse triangle, drawn from filled rectangles rather than a glyph like a
     * geometric-shapes arrow -- three fills beat betting a header on font coverage.
     */
    private void arrow(GuiGraphicsExtractor graphics, int x, int y) {
        if (expanded) {
            graphics.fill(x, y - 1, x + 5, y, ModuleListScreen.ACCENT);
            graphics.fill(x + 1, y, x + 4, y + 1, ModuleListScreen.ACCENT);
            graphics.fill(x + 2, y + 1, x + 3, y + 2, ModuleListScreen.ACCENT);
        } else {
            graphics.fill(x + 1, y - 3, x + 2, y + 2, ModuleListScreen.ACCENT);
            graphics.fill(x + 2, y - 2, x + 3, y + 1, ModuleListScreen.ACCENT);
            graphics.fill(x + 3, y - 1, x + 4, y, ModuleListScreen.ACCENT);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        dragged = false; // a fresh press starts a fresh "was this a drag or a click?"
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        dragged = true;
        onDrag.moveBy(dragX, dragY);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        if (dragged) {
            onDragEnd.run();
        } else {
            onToggle.run();
            playButtonClickSound(Minecraft.getInstance().getSoundManager());
        }
        dragged = false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
