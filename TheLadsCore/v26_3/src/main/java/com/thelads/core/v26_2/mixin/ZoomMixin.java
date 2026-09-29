package com.thelads.core.v26_2.mixin;
import com.thelads.core.v26_2.feature.NativeFeatures;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class ZoomMixin {
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true, require = 1)
    private void ladsWorldFov(float delta, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValueF() * NativeFeatures.zoom(delta, false));
    }
    @Inject(method = "calculateHudFov(F)F", at = @At("RETURN"), cancellable = true, require = 1)
    private void ladsHandFov(float delta, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValueF() * NativeFeatures.zoom(delta, true));
    }
}
