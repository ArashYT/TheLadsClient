package com.thelads.core.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private Object entity;
    @Shadow private Object level;

    @Inject(method = "getCameraEntityPartialTicks", at = @At("HEAD"), cancellable = true)
    private void ladsSafePartialTicks(CallbackInfoReturnable<Float> cir) {
        if (this.entity != null && this.level == null) {
            cir.setReturnValue(1.0F);
        }
    }
}
