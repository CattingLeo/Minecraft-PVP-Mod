package com.catting.pvpkit;

import java.util.List;

import com.catting.pvpkit.MacroConfig.Step;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;

/**
 * A "+"-added duplicate row, spliced into the real vanilla Key Binds list directly under
 * the keybind it mirrors. Deliberately a real KeyBindsList.Entry so vanilla's own list
 * widget positions, scrolls and highlights it exactly like its own rows -- this class
 * only fills in the row's content.
 *
 * Column positions come from MacroRows, which KeyEntryMixin fills in from whatever
 * vanilla actually laid out that frame, so the three buttons sit in exactly the same
 * columns as every vanilla row rather than at coordinates guessed here.
 *
 * Row shape mirrors the vanilla row it duplicates:
 *   [indent arrow]   [trash]   [ key ]   [Reset]
 * The trash sits in the parent row's "+" column, the key button in the parent's key
 * column, and Reset in the parent's Reset column. The name is deliberately NOT repeated
 * -- the arrow shows this row belongs to the keybind above it.
 *
 * The key bound here is this duplicate's own, independent of the original keybind's --
 * that's the point of adding it. Capture is done by MacroKeyCapture at screen level,
 * because a Button never receives raw key events.
 */
public class MacroRowEntry extends KeyBindsList.Entry {

    private static final int ROW_H = 20;
    private static final int RESET_W = 50;
    private static final int GAP = 5;

    private final KeyMapping mapping;
    private final Step step;
    private final Button trashButton;
    private final Button keyButton;
    private final Button resetButton;

    public MacroRowEntry(KeyMapping mapping, Step step) {
        this.mapping = mapping;
        this.step = step;
        // Empty label: the trash glyph is drawn on top of the button in extractContent,
        // since Minecraft's font has no trash character to use as a label.
        this.trashButton = Button.builder(Component.empty(), b -> MacroRows.removeStep(step))
                .bounds(0, 0, MacroRows.plusW(), ROW_H)
                .build();
        this.keyButton = Button.builder(Component.empty(), b -> MacroKeyCapture.begin(this))
                .bounds(0, 0, MacroRows.keyW(), ROW_H)
                .build();
        this.resetButton = Button.builder(Component.translatable("controls.reset"), b -> {
                    step.boundKey = "key.keyboard.unknown";
                    MacroConfig.save();
                    MacroBindings.sync();
                    refreshEntry();
                })
                .bounds(0, 0, RESET_W, ROW_H)
                .build();
        refreshEntry();
    }

    public Step step() {
        return step;
    }

    /** Called by MacroKeyCapture once the player presses the key to bind to this duplicate. */
    public void bind(InputConstants.Key key) {
        step.boundKey = key.getName();
        MacroConfig.save();
        MacroBindings.sync(); // the new key must join vanilla's dispatch map to actually fire
        refreshEntry();
    }

    @Override
    public void refreshEntry() {
        if (MacroKeyCapture.isCapturing(this)) {
            keyButton.setMessage(Component.literal("> ")
                    .append(Component.literal("_").withStyle(ChatFormatting.YELLOW))
                    .append(" <"));
            return;
        }
        InputConstants.Key bound = InputConstants.getKey(step.boundKey);
        boolean unbound = bound == null || bound == InputConstants.UNKNOWN;
        Component name = unbound ? Component.translatable("key.keyboard.unknown") : bound.getDisplayName();
        // Same red-bracket treatment vanilla gives a colliding keybind.
        keyButton.setMessage(!unbound && collides(bound)
                ? Component.literal("[ ").append(name).append(" ]").withStyle(ChatFormatting.RED)
                : name);
        resetButton.active = !unbound;
    }

    /** True if this duplicate's key is already used by a vanilla keybind or another duplicate. */
    private boolean collides(InputConstants.Key bound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            for (KeyMapping other : mc.options.keyMappings) {
                if (other.matches(bound)) return true;
            }
        }
        for (Step other : MacroRows.steps()) {
            if (other != step && bound.getName().equals(other.boundKey)) return true;
        }
        return false;
    }

    @Override
    public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               boolean hovered, float partialTick) {
        int y = getContentY() - 2;
        int keyX = MacroRows.keyX();
        int plusX = MacroRows.plusX();
        if (keyX < 0 || plusX < 0) return; // no vanilla row drawn yet, so columns aren't known

        trashButton.setX(plusX);
        trashButton.setY(y);
        trashButton.setWidth(MacroRows.plusW());
        trashButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawTrash(graphics, plusX + MacroRows.plusW() / 2, y + ROW_H / 2);

        keyButton.setX(keyX);
        keyButton.setY(y);
        keyButton.setWidth(MacroRows.keyW());
        keyButton.extractRenderState(graphics, mouseX, mouseY, partialTick);

        resetButton.setX(keyX + MacroRows.keyW() + GAP);
        resetButton.setY(y);
        resetButton.extractRenderState(graphics, mouseX, mouseY, partialTick);

        drawIndentArrow(graphics, getContentX() + 16, getContentYMiddle(), 0xFFFFFFFF);
    }

    /**
     * The "belongs to the row above" elbow arrow, drawn with plain fill() rectangles
     * rather than a text glyph -- Minecraft's default font has no dependable box-drawing
     * or arrow character, and a missing glyph would render as a blank box.
     */
    private void drawIndentArrow(GuiGraphicsExtractor g, int x, int yMid, int color) {
        int top = yMid - 7;
        g.fill(x, top, x + 2, yMid, color);              // vertical stroke down from the row above
        g.fill(x, yMid - 2, x + 26, yMid, color);        // horizontal stroke to the right
        int tipX = x + 26;
        for (int i = 0; i < 5; i++) {                    // arrowhead, narrowing to the tip
            int half = 4 - i;
            g.fill(tipX + i, yMid - 1 - half, tipX + i + 1, yMid - 1 + half + 1, color);
        }
    }

    /** Small red trash can, drawn with fill() for the same reason as the arrow above. */
    private void drawTrash(GuiGraphicsExtractor g, int cx, int cy, int color) {
        int left = cx - 5;
        int top = cy - 5;
        g.fill(left + 3, top, left + 7, top + 2, color);          // handle
        g.fill(left, top + 2, left + 10, top + 4, color);         // lid
        g.fill(left + 1, top + 4, left + 9, top + 11, color);     // body
    }

    private void drawTrash(GuiGraphicsExtractor g, int cx, int cy) {
        drawTrash(g, cx, cy, 0xFFE04040);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return ImmutableList.of(trashButton, keyButton, resetButton);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return ImmutableList.of(trashButton, keyButton, resetButton);
    }
}
