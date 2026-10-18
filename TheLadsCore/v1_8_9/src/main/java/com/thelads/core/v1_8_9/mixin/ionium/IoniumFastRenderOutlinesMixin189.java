package com.thelads.core.v1_8_9.mixin.ionium;

import com.thelads.core.v1_8_9.render.IoniumFastRender189;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fast Render with Ionium (IoniumFastRender189): spectator outlines draw through framebuffers, which are off meanwhile. */
@Mixin(RenderGlobal.class)
public abstract class IoniumFastRenderOutlinesMixin189 {
    @Inject(method = "isRenderEntityOutlines", at = @At("HEAD"), cancellable = true)
    private void lads$noOutlinesWithoutFramebuffer(CallbackInfoReturnable<Boolean> cir) {
        if (IoniumFastRender189.on()) cir.setReturnValue(false);
    }
}
