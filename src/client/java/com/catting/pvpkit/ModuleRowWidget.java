package com.catting.pvpkit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One module row, drawn flat rather than as a vanilla button so the HUD reads like a
 * client menu instead of an options screen.
 *
 * Extends AbstractWidget rather than doing the drawing and hit-testing by hand in the
 * screen: that way Screen's own widget list handles click dispatch, hover tracking and
 * focus for free. In this MC version the drawing hook is the abstract
 * extractWidgetRenderState (rendering is split out through the render-state extractor --
 * there is no render() to override), verified via javap on AbstractWidget.
 */
public class ModuleRowWidget extends AbstractWidget {

    private final ModuleRegistry.Module module;

    public ModuleRowWidget(int x, int y, int width, int height, ModuleRegistry.Module module) {
        super(x, y, width, height, Component.literal(module.name()));
        this.module = module;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int right = x + this.width;
        boolean on = module.active();

        if (isHovered()) {
            graphics.fill(x, y, right, y + this.height, ModuleListScreen.ROW_HOVER);
        }
        // Accent bar down the left edge -- the at-a-glance "this one is on" marker, the same
        // cue Meteor/LiquidBounce use instead of a checkbox.
        if (on) {
            graphics.fill(x, y, x + 2, y + this.height, ModuleListScreen.ACCENT);
        }

        int textY = y + (this.height - Minecraft.getInstance().font.lineHeight) / 2 + 1;
        graphics.text(Minecraft.getInstance().font, module.name(),
                x + ModuleListScreen.ROW_INDENT, textY,
                on ? ModuleListScreen.TEXT : ModuleListScreen.TEXT_DIM, false);

        String state = module.state();
        int stateWidth = Minecraft.getInstance().font.width(state);
        graphics.text(Minecraft.getInstance().font, state,
                right - ModuleListScreen.PAD - stateWidth, textY,
                on ? ModuleListScreen.ACCENT : ModuleListScreen.TEXT_FAINT, false);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        module.activate();
        playButtonClickSound(Minecraft.getInstance().getSoundManager());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
