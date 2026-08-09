package com.catting.pvpkit.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the vanilla row's private KeyMapping so MacroRows can tell which keybind a row belongs to. */
@Mixin(KeyBindsList.KeyEntry.class)
public interface KeyEntryAccessor {
    @Accessor("key")
    KeyMapping pvpkit$key();
}
