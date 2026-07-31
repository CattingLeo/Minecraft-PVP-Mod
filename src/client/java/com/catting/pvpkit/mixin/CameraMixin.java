package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PvpKitClient;
import net.minecraft.client.Camera;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Blindness/Darkness don't just add fog (handled by FogMixin) -- Camera#extractRenderState
 * separately sets CameraEntityRenderState.doesMobEffectBlockSky from hasEffect(BLINDNESS)/
 * hasEffect(DARKNESS), and LevelRenderer#addSkyPass skips rendering the sky entirely when
 * that's true. That's the actual "dark screen" the player sees; FogMixin alone doesn't
 * touch it, which is why blindness/darkness removal still looked broken after that fix.
 *
 * Camera#extractRenderState only calls hasEffect() twice, for exactly BLINDNESS and
 * DARKNESS (verified against 26.2 bytecode), so redirecting every call here is safe.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean pvpkit$noBlockSky(LivingEntity entity, Holder<MobEffect> effect) {
        if (PvpKitClient.NO_BLINDNESS && effect.is(MobEffects.BLINDNESS)) return false;
        if (PvpKitClient.NO_DARKNESS && effect.is(MobEffects.DARKNESS)) return false;
        return entity.hasEffect(effect);
    }
}
