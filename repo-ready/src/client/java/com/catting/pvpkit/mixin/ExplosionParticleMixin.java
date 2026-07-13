package com.catting.pvpkit.mixin;

import com.catting.pvpkit.PvpKitClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crystal-only explosion removal.
 *
 * A resource pack can't tell a crystal blast from a creeper's (same particle type),
 * but a mod can: EndCrystal removals are tracked (see PvpKitClient), and here we cancel
 * ONLY explosion particles that spawn right where a crystal just was. Creeper/TNT
 * explosions are untouched.
 *
 * The 7-double-parameter handler targets the addParticle(ParticleOptions, x,y,z, dx,dy,dz)
 * overload specifically. If the emitter uses the "force" overload on your build, this is
 * skipped (config is non-required) — flag it and I'll add the second overload.
 */
@Mixin(ClientLevel.class)
public class ExplosionParticleMixin {
    @Inject(method = "addParticle", at = @At("HEAD"), cancellable = true)
    private void pvpkit$noCrystalExplosion(ParticleOptions particle, double x, double y, double z,
                                           double dx, double dy, double dz, CallbackInfo ci) {
        if (!PvpKitClient.NO_CRYSTAL_EXPLOSION) return;
        Object type = particle.getType();
        if ((type == ParticleTypes.EXPLOSION_EMITTER || type == ParticleTypes.EXPLOSION)
                && PvpKitClient.nearRecentCrystal(x, y, z)) {
            ci.cancel();
        }
    }
}
