package com.catting.pvpkit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A clickable category header ("Combat", "Render", ...) that expands or collapses its section. */
public class ModuleCategoryWidget extends AbstractWidget {

    private final ModuleRegistry.Category category;
    private final int count;
    private final boolean expanded;
    private final Runnable onToggle;

    public ModuleCategoryWidget(int x, int y, int width, int height, ModuleRegistry.Category category,
                                int count, boolean expanded, Runnable onToggle) {
        super(x, y, width, height, Component.literal(category.label));
        this.category = category;
        this.count = count;
        this.expanded = expanded;
        this.onToggle = onToggle;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int right = x + this.width;

        graphics.fill(x, y, right, y + this.height, isHovered()
                ? ModuleListScreen.HEADER_BG_HOVER
                : ModuleListScreen.HEADER_BG);

        int textY = y + (this.height - Minecraft.getInstance().font.lineHeight) / 2 + 1;
        arrow(graphics, x + ModuleListScreen.PAD, y + this.height / 2 - 1);
        graphics.text(Minecraft.getInstance().font, category.label,
                x + ModuleListScreen.ROW_INDENT, textY, ModuleListScreen.TEXT, false);

        String tally = String.valueOf(count);
        int tallyWidth = Minecraft.getInstance().font.width(tally);
        graphics.text(Minecraft.getInstance().font, tally,
                right - ModuleListScreen.PAD - tallyWidth, textY, ModuleListScreen.TEXT_FAINT, false);
    }

    /**
     * The expand/collapse triangle, drawn from filled rectangles rather than a glyph like "▶".
     * The default font's coverage of the geometric-shapes block isn't something to bet the whole
     * header on, and three fills are cheaper than a font fallback lookup anyway.
     */
    private void arrow(GuiGraphicsExtractor graphics, int x, int y) {
        if (expanded) {
            // Pointing down: a 5px wide row, then 3px, then 1px.
            graphics.fill(x, y, x + 5, y + 1, ModuleListScreen.ACCENT);
            graphics.fill(x + 1, y + 1, x + 4, y + 2, ModuleListScreen.ACCENT);
            graphics.fill(x + 2, y + 2, x + 3, y + 3, ModuleListScreen.ACCENT);
        } else {
            // Pointing right: a 5px tall column, then 3px, then 1px.
            graphics.fill(x + 1, y - 2, x + 2, y + 3, ModuleListScreen.ACCENT);
            graphics.fill(x + 2, y - 1, x + 3, y + 2, ModuleListScreen.ACCENT);
            graphics.fill(x + 3, y, x + 4, y + 1, ModuleListScreen.ACCENT);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onToggle.run();
        playButtonClickSound(Minecraft.getInstance().getSoundManager());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
