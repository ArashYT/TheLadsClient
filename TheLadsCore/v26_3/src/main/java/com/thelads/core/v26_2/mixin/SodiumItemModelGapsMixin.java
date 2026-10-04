package com.thelads.core.v26_2.mixin;

import com.mojang.math.Quadrant;
import com.thelads.core.client.ItemModelGaps;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium bakes generated item models' edge quads itself (merged runs, ItemModelGenerator.bakeSideFaces never runs): the
 * same overlap as ItemModelGapsMixin. Sodium's push of later layers outwards stays, as every layer moves alike.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.fabric.render.ImprovedItemModelBuilder", remap = false)
public abstract class SodiumItemModelGapsMixin {
    @Inject(method = "bakeQuad(Lnet/minecraft/client/resources/model/ModelBaker$Interner;Lorg/joml/Vector3fc;Lorg/joml/Vector3fc;"
        + "Lnet/minecraft/client/resources/model/cuboid/CuboidFace$UVs;Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;"
        + "Lnet/minecraft/core/Direction;Lnet/minecraft/client/renderer/block/dispatch/ModelState;Ljava/lang/Void;)"
        + "Lnet/minecraft/client/resources/model/geometry/BakedQuad;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ladsOverlap(ModelBaker.Interner interner, Vector3fc from, Vector3fc to, CuboidFace.UVs uvs, BakedQuad.MaterialInfo material,
                             Direction normal, ModelState state, Void extra, CallbackInfoReturnable<BakedQuad> cir) {
        float[] a = {from.x(), from.y(), from.z()}, b = {to.x(), to.y(), to.z()};
        // As in Minecraft's generator, a left or right edge is labelled with the opposite side; its corners make it face out.
        ItemModelGaps.overlap(a, b, -normal.getStepX(), normal.getStepY());
        cir.setReturnValue(FaceBakery.bakeQuad(interner, new Vector3f(a[0], a[1], a[2]), new Vector3f(b[0], b[1], b[2]), uvs, Quadrant.R0,
            material, normal, state, null));
    }
}
