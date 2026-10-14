package com.thelads.core.v1_20_1.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.resources.model.ModelState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Guards {@link FaceBakery#bakeQuad} against concurrent mutation of {@link BlockFaceUV#uvs}.
 * Vanilla mutates {@code face.uv.uvs} in-place during UV-lock/recalculation and restores it at the end of
 * {@code bakeQuad}. When lazy/parallel model baking (e.g. ModernFix dynamic_resources or CTM wrappers) runs across
 * multiple chunk-meshing threads simultaneously on models that inherit elements from a shared parent (stairs, slabs,
 * cube_all), concurrent threads read and overwrite each other's temporary UVs, producing 45-degree diagonal triangle
 * UV shearing on baked quads. Cloning the 4-float UV array per UV-locked bakeQuad call makes it completely thread-safe.
 */
@Mixin(FaceBakery.class)
public abstract class FaceBakerySafetyMixin {
    @ModifyVariable(method = "bakeQuad", at = @At("HEAD"), argsOnly = true, require = 0)
    private BlockElementFace lads$isolateFaceUvs(BlockElementFace face, @Local(argsOnly = true) ModelState modelState) {
        if (face == null || face.uv == null || face.uv.uvs == null || modelState == null || !modelState.isUvLocked()) {
            return face;
        }
        return new BlockElementFace(
            face.cullForDirection,
            face.tintIndex,
            face.texture,
            new BlockFaceUV(face.uv.uvs.clone(), face.uv.rotation)
        );
    }
}
