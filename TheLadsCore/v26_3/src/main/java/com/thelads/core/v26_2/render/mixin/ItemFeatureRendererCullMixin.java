package com.thelads.core.v26_2.render.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.v26_2.render.ItemCull26;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads Flerovium (1.8.5): an item quad the GPU would cull is not written at all (ItemCull26). */
@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererCullMixin {
    @Inject(method = "buildGroup", at = @At("HEAD"))
    private void lads$group(CallbackInfo ci) { ItemCull26.newGroup(); com.thelads.core.perf.RenderQa.itemStart(); }

    @Inject(method = "buildGroup", at = @At("RETURN"))
    private void lads$groupEnd(CallbackInfo ci) { com.thelads.core.perf.RenderQa.itemEnd(); } // QA timing only

    @WrapWithCondition(method = "prepareMainSubmit", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private boolean lads$quad(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance,
                              @Local(argsOnly = true) ItemFeatureRenderer.Submit submit, @Local RenderType renderType) {
        return ItemCull26.keep(submit, pose, submit.displayContext(), renderType, quad);
    }

    @WrapWithCondition(method = "prepareMainSubmit", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuadWithGlint(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V"))
    private boolean lads$glintQuad(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance, PoseStack.Pose decal,
                                   @Local(argsOnly = true) ItemFeatureRenderer.Submit submit, @Local RenderType renderType) {
        return ItemCull26.keep(submit, pose, submit.displayContext(), renderType, quad);
    }
}
