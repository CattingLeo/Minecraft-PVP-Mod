package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PvpKitClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detect the local player's totem pop via entity event 35 (totem of undying).
 * This drives the corner pop. Reliable: handleEntityEvent + event id 35 are long-standing.
 */
@Mixin(LivingEntity.class)
public class TotemMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void pvpkit$onTotem(byte id, CallbackInfo ci) {
        if (id == 35 && ((Object) this) == Minecraft.getInstance().player) {
            PvpKitClient.triggerTotemPop();
        }
    }
}
