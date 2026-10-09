package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.v1_20_1.game.RenderScale1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class RenderScaleMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void lads$beginWorld(float partial, long nanos, boolean renderLevel, CallbackInfo callback) {
        RenderScale1201.beginWorld(minecraft, (GameRenderer) (Object) this);
    }

    // The world pass, entity outlines and spectator post effects are done; vanilla now binds the main target for the HUD.
    @Inject(method = "render", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private void lads$finishWorld(float partial, long nanos, boolean renderLevel, CallbackInfo callback) {
        RenderScale1201.finishWorld(minecraft);
    }

    @Inject(method = "reloadShaders", at = @At("HEAD"))
    private void lads$reload(net.minecraft.server.packs.resources.ResourceProvider provider, CallbackInfo callback) {
        RenderScale1201.reloadShader();
    }
}
