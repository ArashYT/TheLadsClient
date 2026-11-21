package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.thelads.core.v26_2.rentities.RentitiesRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rentities: marks the frame (so models outside the world are never batched) and issues the instanced draws after the solid features. */
@Mixin(LevelRenderer.class)
public class RentitiesLevelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void ladsRentitiesStart(GraphicsResourceAllocator a, boolean b, CameraRenderState c, GpuBufferSlice d, Vector4f e, boolean f, boolean g, CallbackInfo ci) { RentitiesRenderer.levelStart(); }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void ladsRentitiesEnd(GraphicsResourceAllocator a, boolean b, CameraRenderState c, GpuBufferSlice d, Vector4f e, boolean f, boolean g, CallbackInfo ci) { RentitiesRenderer.levelEnd(); }

    @Inject(method = "executeSolid", at = @At("TAIL"), require = 0)
    private void ladsRentitiesDraw(ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass, CallbackInfo ci) {
        RentitiesRenderer.draw(pass);
    }
}
