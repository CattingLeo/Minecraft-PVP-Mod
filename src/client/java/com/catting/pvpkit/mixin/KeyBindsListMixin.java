package com.catting.pvpkit.mixin;

import com.catting.pvpkit.MacroRows;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands MacroRows the live Key Binds list once vanilla has finished populating it, then
 * splices in whatever duplicate rows the active tab has. Injecting at TAIL of the
 * constructor means vanilla's own entries all exist first, so the rebuild only ever
 * inserts alongside them -- it never recreates or reorders a vanilla row.
 */
@Mixin(KeyBindsList.class)
public abstract class KeyBindsListMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void pvpkit$captureList(KeyBindsScreen screen, Minecraft minecraft, CallbackInfo ci) {
        MacroRows.setCurrentList((KeyBindsList) (Object) this);
        MacroRows.rebuild();
    }
}
