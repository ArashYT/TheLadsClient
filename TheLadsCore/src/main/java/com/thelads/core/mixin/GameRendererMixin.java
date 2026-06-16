package com.thelads.core.mixin;

import com.thelads.core.config.CycleOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.mixin.imixin.VBLivingEntityExtension;
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

    @Unique private float lads_partialTick = 1.0f;

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

    // VerticalBobbing: X-axis camera tilt based on vertical velocity, matching vertical-bobbing mod behaviour
    @Inject(method = "bobView", at = @At("TAIL"), require = 0)
    private void ladsVerticalBobbing(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule("VerticalBobbing");
        if (m == null || !m.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        VBLivingEntityExtension ext = (VBLivingEntityExtension) mc.player;
        float rot = Mth.lerp(lads_partialTick, ext.vB$getPrevRot(), ext.vB$getRot());
        poseStack.mulPose(Axis.XP.rotationDegrees(rot));
    }

    // Zoom: inject after extractCamera populates cameraRenderState.projectionMatrix.
    // GameRenderer.getFov was removed in 26.1.2; we scale m00/m11 on the matrix directly
    // (m00 = 1/(aspect*tan(fov/2)), m11 = 1/tan(fov/2) — dividing by mult < 1 zooms in).
    @Inject(method = "extractCamera", at = @At("TAIL"), require = 0)
    private void ladsZoomFov(DeltaTracker dt, float a, float b, CallbackInfo ci) {
        lads_partialTick = dt.getGameTimeDeltaPartialTick(true);
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
