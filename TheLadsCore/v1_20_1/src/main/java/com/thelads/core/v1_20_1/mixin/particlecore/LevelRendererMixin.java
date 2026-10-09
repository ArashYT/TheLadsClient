package com.thelads.core.v1_20_1.mixin.particlecore;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.ParticleCore;
import com.thelads.core.v1_20_1.feature.ParticleCore1201;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The frame's view frustum, read from the matrices the level (and so its particles) is drawn with. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    private static final float[] LADS_PROJECTION = new float[16], LADS_VIEW = new float[16];

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ladsFrustum(PoseStack poseStack, float partialTick, long nanos, boolean outline, Camera camera, GameRenderer gameRenderer,
                             LightTexture lightTexture, Matrix4f projection, CallbackInfo ci) {
        ParticleCore.refresh();
        if (!ParticleCore.culling()) { ParticleCore.FRUSTUM.clear(); ParticleCore1201.frameCamera = null; return; }
        projection.get(LADS_PROJECTION);
        poseStack.last().pose().get(LADS_VIEW);
        ParticleCore.FRUSTUM.set(LADS_PROJECTION, LADS_VIEW);
        ParticleCore1201.frameCamera = camera;
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void ladsFrustumEnd(PoseStack poseStack, float partialTick, long nanos, boolean outline, Camera camera, GameRenderer gameRenderer,
                                LightTexture lightTexture, Matrix4f projection, CallbackInfo ci) {
        ParticleCore.FRUSTUM.clear();
        ParticleCore1201.frameCamera = null;
    }
}
