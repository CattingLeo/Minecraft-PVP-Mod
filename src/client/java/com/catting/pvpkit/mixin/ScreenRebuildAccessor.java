package com.catting.pvpkit.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes protected Screen#rebuildWidgets() so ScrollHotbarMixin can force the Key
 * Binds list to redraw immediately after binding the scroll wheel to a row -- see
 * that mixin's javadoc for why the row's displayed key name otherwise stays stale
 * until the row is clicked again.
 */
@Mixin(Screen.class)
public interface ScreenRebuildAccessor {
    @Invoker("rebuildWidgets")
    void pvpkit$rebuildWidgets();
}
