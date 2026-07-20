package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PvpKitClient;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the vision-reducing fog from Blindness and (Warden) Darkness.
 *
 * In 26.x fog is split into "fog environments"; the mob-effect fogs share the base
 * class MobEffectFogEnvironment, whose isApplicable() gates whether that fog runs.
 * We make it report "not applicable" for Blindness/Darkness, so their fog never
 * activates. Water/lava/powder-snow/nether/dimension fog use different classes and
 * are untouched. (See the research report for the verified code + a setupFog fallback.)
 *
 * NOTE: getMobEffect()'s exact return type and isApplicable()'s name are the two
 * version-sensitive spots. Config is non-required, so a miss is skipped, never a crash.
 *
 * BUG FIXED: this originally compared Holders with `==`, which silently never matched
 * (Holder identity isn't guaranteed equal to a same-effect constant elsewhere), so
 * blindness/darkness removal did nothing even though the mixin "worked". Holder#is(Holder)
 * is the correct comparison -- it's marked @Deprecated by Mojang (a nudge toward more
 * specific overloads like is(ResourceKey)/is(TagKey), not a sign it's broken or going away)
 * but is fully functional and verified fixing the bug.
 */
@Mixin(MobEffectFogEnvironment.class)
public abstract class FogMixin {

    @Invoker("getMobEffect")
    abstract Holder<MobEffect> pvpkit$getMobEffect();

    @Inject(method = "isApplicable", at = @At("RETURN"), cancellable = true)
    private void pvpkit$removeEffectFog(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return; // already off
        Holder<MobEffect> effect = pvpkit$getMobEffect();
        boolean blind = PvpKitClient.NO_BLINDNESS && effect.is(MobEffects.BLINDNESS);
        boolean dark  = PvpKitClient.NO_DARKNESS  && effect.is(MobEffects.DARKNESS);
        if (blind || dark) {
            cir.setReturnValue(false);
        }
    }
}
