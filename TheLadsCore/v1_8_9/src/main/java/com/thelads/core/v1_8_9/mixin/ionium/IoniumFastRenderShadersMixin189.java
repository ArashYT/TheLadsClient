package com.thelads.core.v1_8_9.mixin.ionium;

import com.thelads.core.v1_8_9.render.IoniumFastRender189;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fast Render with Ionium (IoniumFastRender189): vanilla's post shaders (spectating a mob, Super Secret Settings) need the main framebuffer. */
@Mixin(EntityRenderer.class)
public abstract class IoniumFastRenderShadersMixin189 {
    @Inject(method = "loadShader", at = @At("HEAD"), cancellable = true)
    private void lads$noShaderWithoutFramebuffer(ResourceLocation location, CallbackInfo ci) {
        if (IoniumFastRender189.on()) ci.cancel();
    }
}
