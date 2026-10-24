package com.thelads.core.v26_2.feature.pvp.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Glint Colorizer: a render type writes its transforms (model view, texture scroll) right before it draws; a glint one (its texture
 * transform is a glint scroll) gets the module's colour as the glint shader's ColorModulator, which RenderType leaves white.
 */
@Mixin(RenderType.class)
public class PvpGlintMixin26 {
    @WrapOperation(method = "writeDynamicTransforms", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/rendertype/TextureTransform;createMatrix()Lorg/joml/Matrix4f;"))
    private Matrix4f lads$glintScroll(TextureTransform transform, Operation<Matrix4f> original) {
        PvpVisuals26.glintNow = PvpVisuals26.isGlint(transform);
        return original.call(transform);
    }

    @WrapOperation(method = "writeDynamicTransforms", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicGpuData;"
        + "writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
    private GpuBufferSlice lads$glintColor(DynamicGpuData data, Matrix4f modelView, Matrix4f texture, Operation<GpuBufferSlice> original) {
        if (!PvpVisuals26.glintNow) return original.call(data, modelView, texture);
        PvpVisuals26.glintNow = false;
        return data.writeTransform(modelView, PvpVisuals26.GLINT_COLOR, PvpVisuals26.NO_OFFSET, texture);
    }
}
