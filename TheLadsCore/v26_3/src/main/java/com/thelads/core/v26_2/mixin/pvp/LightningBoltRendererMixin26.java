package com.thelads.core.v26_2.mixin.pvp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.entity.state.LightningBoltRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render Tweaks, Hide Lightning: the bolt is not submitted (its sound and sky flash stay). */
@Mixin(LightningBoltRenderer.class)
public class LightningBoltRendererMixin26 {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LightningBoltRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void lads$hideLightning(LightningBoltRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (PvpVisuals.hideLightning) ci.cancel();
    }
}
