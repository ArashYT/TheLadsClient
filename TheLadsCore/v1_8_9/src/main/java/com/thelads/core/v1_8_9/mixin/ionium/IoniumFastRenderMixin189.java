package com.thelads.core.v1_8_9.mixin.ionium;

import com.thelads.core.v1_8_9.render.IoniumFastRender189;
import net.minecraft.client.renderer.OpenGlHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fast Render with Ionium (IoniumFastRender189): no main framebuffer while it is on, as under OptiFine's Fast Render. */
@Mixin(OpenGlHelper.class)
public abstract class IoniumFastRenderMixin189 {
    @Inject(method = "isFramebufferEnabled", at = @At("HEAD"), cancellable = true)
    private static void lads$fastRender(CallbackInfoReturnable<Boolean> cir) {
        if (IoniumFastRender189.on()) cir.setReturnValue(false);
    }
}
