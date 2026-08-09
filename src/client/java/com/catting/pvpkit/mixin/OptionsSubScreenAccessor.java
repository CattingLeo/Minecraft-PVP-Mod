package com.catting.pvpkit.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes protected fields so KeyBindsRedirectMixin can pull the (parent, options) it needs to open KeybindsMacrosScreen in place of the real KeyBindsScreen. */
@Mixin(OptionsSubScreen.class)
public interface OptionsSubScreenAccessor {
    @Accessor("lastScreen")
    Screen pvpkit$getLastScreen();

    @Accessor("options")
    Options pvpkit$getOptions();
}
