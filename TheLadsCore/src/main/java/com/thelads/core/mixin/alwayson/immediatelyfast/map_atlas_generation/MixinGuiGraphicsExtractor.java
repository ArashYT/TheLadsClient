/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.wrapoperation.Operation
 *  com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
 *  com.llamalad7.mixinextras.sugar.Local
 *  com.mojang.blaze3d.pipeline.RenderPipeline
 *  com.mojang.blaze3d.textures.GpuSampler
 *  com.mojang.blaze3d.textures.GpuTextureView
 *  net.minecraft.client.renderer.state.MapRenderState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.map_atlas_generation;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import com.thelads.core.features.alwayson.immediatelyfast.feature.map_atlas_generation.MapAtlasTexture;
import com.thelads.core.features.alwayson.immediatelyfast.injection.interfaces.IMapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value={GuiGraphicsExtractor.class})
public abstract class MixinGuiGraphicsExtractor {
    @WrapOperation(method={"map"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIFFFFI)V", ordinal=0)})
    private void modifyTextureCoordinates(GuiGraphicsExtractor instance, RenderPipeline pipeline, GpuTextureView textureView, GpuSampler sampler, int x1, int y1, int x2, int y2, float u1, float u2, float v1, float v2, int color, Operation<Void> original, @Local(argsOnly=true) MapRenderState renderState) {
        IMapRenderState immediatelyFast$renderState = (IMapRenderState)renderState;
        if (immediatelyFast$renderState.immediatelyFast$getAtlasTexture() != null && immediatelyFast$renderState.immediatelyFast$getAtlasTexture().getTextureId().equals(renderState.texture)) {
            u1 = (float)immediatelyFast$renderState.immediatelyFast$getAtlasX() / (float)MapAtlasTexture.ATLAS_SIZE;
            u2 = (float)(immediatelyFast$renderState.immediatelyFast$getAtlasX() + 128) / (float)MapAtlasTexture.ATLAS_SIZE;
            v1 = (float)immediatelyFast$renderState.immediatelyFast$getAtlasY() / (float)MapAtlasTexture.ATLAS_SIZE;
            v2 = (float)(immediatelyFast$renderState.immediatelyFast$getAtlasY() + 128) / (float)MapAtlasTexture.ATLAS_SIZE;
        }
        original.call(new Object[]{instance, pipeline, textureView, sampler, x1, y1, x2, y2, Float.valueOf(u1), Float.valueOf(u2), Float.valueOf(v1), Float.valueOf(v2), color});
    }
}
