package com.catting.pvpkit.mixin;

import java.util.ArrayList;
import java.util.List;

import com.catting.pvpkit.MacroRows;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a "+" button to every row of the REAL vanilla Key Binds screen -- and nothing
 * else. This is the whole point of doing it as a mixin rather than as a lookalike
 * screen of our own: every other pixel (row heights, name column, the key button, the
 * Reset button, category headers, row highlighting, the scrollbar, collision warnings)
 * stays exactly vanilla, because it IS vanilla.
 *
 * The "+" is placed 5px to the left of vanilla's change-button, mirroring the same
 * 5px gap vanilla itself puts between its change and Reset buttons. Position is read
 * off changeButton AFTER vanilla has positioned it (this injects at TAIL of
 * extractContent), so it follows vanilla's own layout automatically instead of
 * re-deriving it from constants that could drift.
 *
 * children()/narratables() are extended too, otherwise the button would draw but never
 * receive clicks -- ContainerObjectSelectionList dispatches input purely through
 * children().
 */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyEntryMixin {

    @Unique
    private static final int PVPKIT_PLUS_W = 20;

    @Shadow
    @Final
    private KeyMapping key;

    @Shadow
    @Final
    private Button changeButton;

    @Unique
    private Button pvpkit$plusButton;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void pvpkit$createPlus(KeyBindsList list, KeyMapping mapping, Component name, CallbackInfo ci) {
        pvpkit$plusButton = Button.builder(Component.literal("+"), b -> MacroRows.addStep(this.key))
                .bounds(0, 0, PVPKIT_PLUS_W, 20)
                .build();
    }

    @Inject(method = "extractContent", at = @At("TAIL"))
    private void pvpkit$renderPlus(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick, CallbackInfo ci) {
        if (pvpkit$plusButton == null) return;
        pvpkit$plusButton.setX(changeButton.getX() - 5 - pvpkit$plusButton.getWidth());
        pvpkit$plusButton.setY(changeButton.getY());
        pvpkit$plusButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // Publish the columns vanilla just laid out, so duplicate rows can line up with
        // them exactly instead of re-deriving positions that could drift out of sync.
        MacroRows.publishColumns(pvpkit$plusButton.getX(), pvpkit$plusButton.getWidth(),
                changeButton.getX(), changeButton.getWidth());
    }

    @Inject(method = "children", at = @At("RETURN"), cancellable = true)
    private void pvpkit$children(CallbackInfoReturnable<List<? extends GuiEventListener>> cir) {
        if (pvpkit$plusButton == null) return;
        List<GuiEventListener> out = new ArrayList<>(cir.getReturnValue());
        out.add(pvpkit$plusButton);
        cir.setReturnValue(out);
    }

    @Inject(method = "narratables", at = @At("RETURN"), cancellable = true)
    private void pvpkit$narratables(CallbackInfoReturnable<List<? extends NarratableEntry>> cir) {
        if (pvpkit$plusButton == null) return;
        List<NarratableEntry> out = new ArrayList<>(cir.getReturnValue());
        out.add(pvpkit$plusButton);
        cir.setReturnValue(out);
    }
}
