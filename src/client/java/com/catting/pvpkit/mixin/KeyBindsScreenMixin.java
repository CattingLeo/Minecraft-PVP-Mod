package com.catting.pvpkit.mixin;

import com.catting.pvpkit.MacroKeyCapture;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Routes raw input to a duplicate row that's waiting to be bound, before vanilla's own
 * "am I rebinding a keybind?" handling gets it. Buttons never receive keyboard events,
 * so a duplicate row's key button can only start a capture -- the actual keypress has to
 * be read here, at screen level, exactly like vanilla reads one for its own rebinding.
 *
 * Covers keyboard keys and mouse buttons (scroll included: this mod already registers
 * the wheel as InputConstants MOUSE button 10 -- see ScrollKeybind/ScrollHotbarMixin --
 * so a wheel notch arrives here as an ordinary mouse button and binds like any other).
 * Escape cancels, matching vanilla's unbind-key convention.
 */
@Mixin(KeyBindsScreen.class)
public abstract class KeyBindsScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void pvpkit$captureKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!MacroKeyCapture.active()) return;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            MacroKeyCapture.cancel();
        } else {
            MacroKeyCapture.accept(InputConstants.Type.KEYSYM.getOrCreate(event.key()));
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void pvpkit$captureMouse(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!MacroKeyCapture.active()) return;
        MacroKeyCapture.accept(InputConstants.Type.MOUSE.getOrCreate(event.button()));
        cir.setReturnValue(true);
    }
}
