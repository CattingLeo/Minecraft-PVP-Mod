package com.catting.pvpkit.mixin;

import com.catting.pvpkit.MacroBindings;

import net.minecraft.client.KeyMapping;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KeyMapping.resetMapping() rebuilds MAP from scratch out of each mapping's single bound
 * key, which silently discards every extra key MacroBindings registered. Vanilla calls it
 * whenever a keybind is changed or reset, so without this the extra keys would work right
 * up until the first time the Key Binds screen is touched and then quietly stop firing.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingResetMixin {

    @Inject(method = "resetMapping", at = @At("TAIL"))
    private static void pvpkit$reapplyExtraKeys(CallbackInfo ci) {
        MacroBindings.sync();
    }
}
