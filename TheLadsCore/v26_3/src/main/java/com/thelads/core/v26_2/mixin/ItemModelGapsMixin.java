package com.thelads.core.v26_2.mixin;

import com.mojang.math.Quadrant;
import com.thelads.core.client.ItemModelGaps;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.CuboidRotation;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.cuboid.ItemModelGenerator;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** No see-through seams in generated item models (ItemModelGaps): each edge quad overlaps the faces it meets. */
@Mixin(ItemModelGenerator.class)
public abstract class ItemModelGapsMixin {
    @Redirect(method = "bakeSideFaces", require = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/cuboid/FaceBakery;"
        + "bakeQuad(Lnet/minecraft/client/resources/model/ModelBaker$Interner;Lorg/joml/Vector3fc;Lorg/joml/Vector3fc;"
        + "Lnet/minecraft/client/resources/model/cuboid/CuboidFace$UVs;Lcom/mojang/math/Quadrant;"
        + "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;Lnet/minecraft/core/Direction;"
        + "Lnet/minecraft/client/renderer/block/dispatch/ModelState;Lnet/minecraft/client/resources/model/cuboid/CuboidRotation;)"
        + "Lnet/minecraft/client/resources/model/geometry/BakedQuad;"))
    private static BakedQuad ladsOverlap(ModelBaker.Interner interner, Vector3fc from, Vector3fc to, CuboidFace.UVs uvs, Quadrant rotation,
                                        BakedQuad.MaterialInfo material, Direction facing, ModelState state, CuboidRotation elementRotation) {
        float[] a = {from.x(), from.y(), from.z()}, b = {to.x(), to.y(), to.z()};
        // A left or right edge quad is labelled with the opposite side (EAST on a pixel's left edge); its corners make it face out.
        ItemModelGaps.overlap(a, b, -facing.getStepX(), facing.getStepY());
        return FaceBakery.bakeQuad(interner, new Vector3f(a[0], a[1], a[2]), new Vector3f(b[0], b[1], b[2]), uvs, rotation, material, facing,
            state, elementRotation);
    }
}
