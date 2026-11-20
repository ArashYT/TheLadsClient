package com.thelads.core.v1_21_11.ladsapi.minigame.world.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.thelads.core.client.ladsapi.minigame.FxState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import com.thelads.core.client.ladsapi.minigame.CameraState;
import com.thelads.core.v1_21_11.ladsapi.minigame.world.CameraRender12111;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minigame fx shaders on 1.21.11: vanilla keeps one post effect (the creeper/spider/invert spectator chain) and processes it in render();
 * ours is processed right after it on the same target, so both stack and vanilla's is never replaced. The held item hides with the HUD.
 */
@Mixin(GameRenderer.class)
public abstract class MgGameRendererMixin12111 {
    private static final Identifier BLUR = Identifier.fromNamespaceAndPath("theladscore", "mg_blur");
    private static final Identifier GRAYSCALE = Identifier.fromNamespaceAndPath("theladscore", "mg_grayscale");
    private static final Identifier SEPIA = Identifier.fromNamespaceAndPath("theladscore", "mg_sepia");
    private static final Identifier INVERT = Identifier.fromNamespaceAndPath("theladscore", "mg_invert");

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void lads$fxShader(net.minecraft.client.DeltaTracker tracker, boolean advance, CallbackInfo ci) {
        FxState.Shader shader = Minigame.fx.shader(Minigame.now());
        if (shader == FxState.Shader.NONE) return;
        Identifier id = switch (shader) {
            case BLUR -> BLUR;
            case GRAYSCALE -> GRAYSCALE;
            case SEPIA -> SEPIA;
            default -> INVERT;
        };
        PostChain chain = minecraft.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
        if (chain != null) chain.process(minecraft.getMainRenderTarget(), resourcePool);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void lads$shotFov(Camera camera, float partial, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        CameraState.Pose p = CameraRender12111.current();
        if (useFovSetting && p != null) cir.setReturnValue((float) p.fov());
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void lads$noHand(CallbackInfo ci) {
        if (Minigame.camera.hidesHud(Minigame.now())) ci.cancel();
    }
}
