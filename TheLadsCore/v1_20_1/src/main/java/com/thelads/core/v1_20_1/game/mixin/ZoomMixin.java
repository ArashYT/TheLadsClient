package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class ZoomMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)D", at = @At("RETURN"), cancellable = true, require = 1)
    private void ladsFov(Camera camera, float delta, boolean world, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue((double) NativeFeatures1201.fov((float) cir.getReturnValueD(), !world));
    }
}
