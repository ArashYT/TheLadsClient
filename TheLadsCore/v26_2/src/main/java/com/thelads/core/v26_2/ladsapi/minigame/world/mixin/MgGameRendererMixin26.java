package com.thelads.core.v26_2.ladsapi.minigame.world.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.thelads.core.client.ladsapi.minigame.FxState;
import com.thelads.core.client.ladsapi.minigame.Minigame;
import net.minecraft.client.Minecraft;
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
 * Minigame fx shaders on 26.2: vanilla keeps one post effect (the creeper/spider/invert spectator chain) and processes it in render();
 * ours is processed right after it on the same target, so both stack and vanilla's is never replaced. The held item hides with the HUD.
 */
@Mixin(GameRenderer.class)
public abstract class MgGameRendererMixin26 {
    private static final Identifier BLUR = Identifier.fromNamespaceAndPath("theladscore", "mg_blur");
    private static final Identifier GRAYSCALE = Identifier.fromNamespaceAndPath("theladscore", "mg_grayscale");
    private static final Identifier SEPIA = Identifier.fromNamespaceAndPath("theladscore", "mg_sepia");
    private static final Identifier INVERT = Identifier.fromNamespaceAndPath("theladscore", "mg_invert");

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private RenderTarget mainRenderTarget;
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
        if (chain != null) chain.process(mainRenderTarget, resourcePool);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void lads$noHand(CallbackInfo ci) {
        if (Minigame.camera.hidesHud(Minigame.now())) ci.cancel();
    }
}
