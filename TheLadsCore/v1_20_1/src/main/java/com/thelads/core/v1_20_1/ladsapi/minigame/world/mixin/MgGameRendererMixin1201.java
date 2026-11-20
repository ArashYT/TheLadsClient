package com.thelads.core.v1_20_1.ladsapi.minigame.world.mixin;

import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import com.thelads.core.v1_20_1.ladsapi.minigame.world.CameraRender1201;
import com.thelads.core.v1_20_1.ladsapi.minigame.world.FxPost1201;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minigame fx shaders, the shot's field of view and the hidden hand on 1.20.1. Runs after the other Lads world hooks (priority) so its
 * post effect sees the finished picture; vanilla's own post effect is processed before the point used here and is left alone.
 */
@Mixin(value = GameRenderer.class, priority = 1100)
public abstract class MgGameRendererMixin1201 {
    @Shadow @Final private Minecraft minecraft;

    // Same point as RenderScaleMixin: the world pass and vanilla's post effect are done, vanilla now binds the main target for the HUD.
    @Inject(method = "render", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private void lads$fxShader(float partial, long nanos, boolean renderLevel, CallbackInfo ci) {
        FxPost1201.afterWorld(minecraft);
    }

    @Inject(method = "reloadShaders", at = @At("HEAD"))
    private void lads$fxReload(net.minecraft.server.packs.resources.ResourceProvider provider, CallbackInfo ci) {
        FxPost1201.reload();
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void lads$shotFov(Camera camera, float partial, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        if (!useFovSetting) return;
        CameraState.Pose p = CameraRender1201.current();
        if (p != null) cir.setReturnValue(p.fov());
        CameraRender1201.fov = cir.getReturnValueD();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void lads$noHand(CallbackInfo ci) {
        if (CameraRender1201.current() != null || Minigame.camera.hidesHud(Minigame.now())) ci.cancel(); // the shot's view has no held item
    }
}
