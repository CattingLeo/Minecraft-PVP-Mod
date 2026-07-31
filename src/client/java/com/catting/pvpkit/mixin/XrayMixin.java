package com.catting.pvpkit.mixin;

import com.catting.pvpkit.XrayBlocks;

import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xray. Two hooks are needed, not one: getRenderShape() hides the block
 * itself, and canOcclude() stops it from culling the faces of a NEIGHBOURING
 * still-visible block -- an ore touching now-invisible stone needs that face
 * to actually render, or it'd look like the ore has a hole where the stone
 * used to "block" it.
 *
 * Both live on BlockBehaviour$BlockStateBase -- common-package, BlockState-
 * level logic, not renderer code. That's deliberate: Sodium replaces
 * SectionCompiler/SectionRenderDispatcher/LevelRenderer wholesale, but not
 * Block/BlockState/BlockBehaviour, so Sodium's own independent mesh builder
 * still calls these same two methods when deciding what to bake. Mixing into
 * the vanilla chunk-mesh classes instead would be silently dead code under
 * Sodium -- the standard "Sodium breaks naive Xray" failure mode this avoids.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class XrayMixin {

    @Inject(method = "getRenderShape", at = @At("RETURN"), cancellable = true)
    private void pvpkit$xrayRenderShape(CallbackInfoReturnable<RenderShape> cir) {
        BlockBehaviour.BlockStateBase self = (BlockBehaviour.BlockStateBase) (Object) this;
        if (!XrayBlocks.isVisible(self)) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }

    @Inject(method = "canOcclude", at = @At("RETURN"), cancellable = true)
    private void pvpkit$xrayCanOcclude(CallbackInfoReturnable<Boolean> cir) {
        BlockBehaviour.BlockStateBase self = (BlockBehaviour.BlockStateBase) (Object) this;
        if (!XrayBlocks.isVisible(self)) {
            cir.setReturnValue(false);
        }
    }
}
