package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins;


import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_11.embedded.emf.EMF;
import com.thelads.core.v1_21_11.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v1_21_11.embedded.emf.utils.EMFLODHandler;

/**
 * This mixin is used to get the current FOV value from the game renderer.
 * less impactful than collecting all the values required and calling the method itself
 */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "getFov",
            at = @At(value = "RETURN"))
    private void emf$captureFov(final CallbackInfoReturnable<
                    Float
                    > cir) {
        if (EMF.config().getConfig().animationLODDistance != 0) {
            EMFLODHandler.lastFOV = (double) cir.getReturnValue();
        }
    }

    @Inject(method = "render",
            at = @At(value = "HEAD"))
    private void emf$injectCounter(final CallbackInfo ci) {
        EMFState.incFrameCount();
    }
}