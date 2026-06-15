package com.thelads.core.mixin;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.modules.ZoomModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final private GameRenderState gameRenderState;

    @Unique private float ladsSmoothedVY = 0f;
    @Unique private boolean ladsVYInit = false;

    // OldDamageTilt: stronger directional screen roll on damage
    @Inject(method = "bobHurt", at = @At("TAIL"), require = 0)
    private void ladsOldDamageTilt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("OldDamageTilt");
        if (m == null || !m.isEnabled()) return;

        var ers = cameraState.entityRenderState;
        if (!ers.isLiving || ers.hurtTime <= 0.0F || ers.hurtDuration <= 0) return;

        float t     = ers.hurtTime / ers.hurtDuration;
        float hurt  = Mth.sin(t * t * t * t * (float) Math.PI);
        float rr    = ers.hurtDir;

        float maxTilt = 5.0F;
        Option io = m.getOption("Intensity");
        if (io instanceof CycleOption) {
            maxTilt = new float[]{ 3.0F, 5.0F, 9.0F }[((CycleOption) io).getIndex()];
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(-rr));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-hurt * maxTilt));
        poseStack.mulPose(Axis.YP.rotationDegrees(rr));
    }

    // VerticalBobbing: smooth vertical view bob when jumping / falling
    @Inject(method = "bobView", at = @At("TAIL"), require = 0)
    private void ladsVerticalBobbing(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("VerticalBobbing");
        if (m == null || !m.isEnabled()) return;

        var ers = cameraState.entityRenderState;
        if (!ers.isPlayer) return;

        // Constant vertical component of vanilla bob (no extra multiplier needed)
        float walk = ers.backwardsInterpolatedWalkDistance;
        float bob  = ers.bob;
        poseStack.translate(0.0F, -Math.abs(Mth.cos(walk * (float) Math.PI) * bob) * 0.5F, 0.0F);

        // Smooth vertical velocity from player Y-delta to avoid per-tick snap
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            float rawVY = (float) (mc.player.getY() - mc.player.yOld);
            
            // To prevent jitter, we use a very soft spring that smooths out the 20Hz tick rate
            if (!ladsVYInit) {
                ladsSmoothedVY = rawVY;
                ladsVYInit = true;
            } else {
                // Ultra-smooth lerp (approx frame-rate independent if FPS is high, but soft enough to hide tick jitter)
                ladsSmoothedVY += (rawVY - ladsSmoothedVY) * 0.08f;
            }

            float intensity = 1.0f;
            Option vbIntensityOpt = m.getOption("Intensity");
            if (vbIntensityOpt instanceof CycleOption) {
                intensity = new float[]{0.4f, 1.0f, 2.0f}[((CycleOption) vbIntensityOpt).getIndex()];
            }
            
            // Apply a cubic easing to the smoothed velocity to make the turn-around points softer
            float easedVY = ladsSmoothedVY * ladsSmoothedVY * ladsSmoothedVY * 5.0f;
            poseStack.translate(0.0F, Mth.clamp(easedVY, -0.5f, 0.5f) * intensity, 0.0F);
        }
    }

    // Zoom: inject after extractCamera populates cameraRenderState.projectionMatrix.
    // GameRenderer.getFov was removed in 26.1.2; we scale m00/m11 on the matrix directly
    // (m00 = 1/(aspect*tan(fov/2)), m11 = 1/tan(fov/2) — dividing by mult < 1 zooms in).
    @Inject(method = "extractCamera", at = @At("TAIL"), require = 0)
    private void ladsZoomFov(DeltaTracker dt, float a, float b, CallbackInfo ci) {
        ZoomModule zoom = (ZoomModule) ModuleManager.getInstance().getModule("Zoom");
        if (zoom == null || !zoom.isActive()) return;
        float mult = zoom.getFovMultiplier(dt.getGameTimeDeltaPartialTick(false));
        if (mult >= 1.0f) return;
        CameraRenderState camState = gameRenderState.levelRenderState.cameraRenderState;
        if (camState == null || !camState.initialized || camState.projectionMatrix == null) return;
        camState.projectionMatrix.m00(camState.projectionMatrix.m00() / mult);
        camState.projectionMatrix.m11(camState.projectionMatrix.m11() / mult);
    }
}
