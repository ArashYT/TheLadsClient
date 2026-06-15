/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.math.Axis
 *  net.minecraft.client.DeltaTracker
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.GameRenderer
 *  net.minecraft.client.renderer.state.GameRenderState
 *  net.minecraft.client.renderer.state.level.CameraEntityRenderState
 *  net.minecraft.client.renderer.state.level.CameraRenderState
 *  net.minecraft.util.Mth
 *  org.joml.Quaternionfc
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ZoomModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={GameRenderer.class})
public class GameRendererMixin {
    @Shadow
    @Final
    private GameRenderState gameRenderState;
    @Unique
    private float ladsSmoothedVY = 0.0f;
    @Unique
    private boolean ladsVYInit = false;

    @Inject(method={"bobHurt"}, at={@At(value="TAIL")}, require=0)
    private void ladsOldDamageTilt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("OldDamageTilt");
        if (m == null || !m.isEnabled()) {
            return;
        }
        CameraEntityRenderState ers = cameraState.entityRenderState;
        if (!ers.isLiving || ers.hurtTime <= 0.0f || ers.hurtDuration <= 0) {
            return;
        }
        float t = ers.hurtTime / (float)ers.hurtDuration;
        float hurt = Mth.sin((double)(t * t * t * t * (float)Math.PI));
        float rr = ers.hurtDir;
        float maxTilt = 5.0f;
        Option io = m.getOption("Intensity");
        if (io instanceof CycleOption) {
            maxTilt = (new float[]{3.0f, 5.0f, 9.0f})[((CycleOption)io).getIndex()];
        }
        poseStack.mulPose((Quaternionfc)Axis.YP.rotationDegrees(-rr));
        poseStack.mulPose((Quaternionfc)Axis.ZP.rotationDegrees(-hurt * maxTilt));
        poseStack.mulPose((Quaternionfc)Axis.YP.rotationDegrees(rr));
    }

    @Inject(method={"bobView"}, at={@At(value="TAIL")}, require=0)
    private void ladsVerticalBobbing(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("VerticalBobbing");
        if (m == null || !m.isEnabled()) {
            return;
        }
        CameraEntityRenderState ers = cameraState.entityRenderState;
        if (!ers.isPlayer) {
            return;
        }
        float walk = ers.backwardsInterpolatedWalkDistance;
        float bob = ers.bob;
        poseStack.translate(0.0f, -Math.abs(Mth.cos((double)(walk * (float)Math.PI)) * bob) * 0.5f, 0.0f);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            float rawVY = (float)Mth.clamp((double)(mc.player.getY() - mc.player.yOld), (double)-0.6, (double)0.6);
            if (!this.ladsVYInit) {
                this.ladsSmoothedVY = rawVY;
                this.ladsVYInit = true;
            } else {
                float alpha = rawVY != 0.0f ? 0.35f : 0.15f;
                this.ladsSmoothedVY = Mth.lerp((float)alpha, (float)this.ladsSmoothedVY, (float)rawVY);
            }
            float intensity = 1.0f;
            Option vbIntensityOpt = m.getOption("Intensity");
            if (vbIntensityOpt instanceof CycleOption) {
                intensity = (new float[]{0.4f, 1.0f, 2.0f})[((CycleOption)vbIntensityOpt).getIndex()];
            }
            poseStack.translate(0.0f, this.ladsSmoothedVY * 0.2f * intensity, 0.0f);
        }
    }

    @Inject(method={"extractCamera"}, at={@At(value="TAIL")}, require=0)
    private void ladsZoomFov(DeltaTracker dt, float a, float b, CallbackInfo ci) {
        ZoomModule zoom = (ZoomModule)ModuleManager.getInstance().getModule("Zoom");
        if (zoom == null || !zoom.isActive()) {
            return;
        }
        float mult = zoom.getFovMultiplier(dt.getGameTimeDeltaPartialTick(false));
        if (mult >= 1.0f) {
            return;
        }
        CameraRenderState camState = this.gameRenderState.levelRenderState.cameraRenderState;
        if (camState == null || !camState.initialized || camState.projectionMatrix == null) {
            return;
        }
        camState.projectionMatrix.m00(camState.projectionMatrix.m00() / mult);
        camState.projectionMatrix.m11(camState.projectionMatrix.m11() / mult);
    }
}

