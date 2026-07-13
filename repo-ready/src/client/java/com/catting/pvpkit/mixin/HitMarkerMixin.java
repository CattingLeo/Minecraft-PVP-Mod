package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PvpKitClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires the hit marker when the local player attacks an entity.
 *
 * Player#attack(Entity) is invoked client-side the moment a left-click-on-target
 * swing is processed (this is the same hook classic "hit marker" mods use).
 *
 * Honest caveat: this fires whenever an attack swing is processed, which can
 * include hits fully absorbed by the target's brief post-hit invulnerability
 * window. It's a cosmetic "you swung and connected" signal, not a guaranteed
 * "damage was dealt" one.
 */
@Mixin(Player.class)
public class HitMarkerMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void pvpkit$onAttack(Entity target, CallbackInfo ci) {
        if (PvpKitClient.SHOW_HIT_MARKER && (Object) this == Minecraft.getInstance().player) {
            PvpKitClient.triggerHitMarker();
        }
    }
}
