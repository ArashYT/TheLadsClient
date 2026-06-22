package com.thelads.core.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "getCameraEntityPartialTicks", at = @At("HEAD"), cancellable = true)
    private void ladsSafePartialTicks(CallbackInfoReturnable<Float> cir) {
        // If the game level isn't loaded yet (e.g., joining a server transition),
        // we cannot calculate partial ticks because it relies on the level's tick manager.
        if (Minecraft.getInstance().level == null) {
            cir.setReturnValue(1.0F);
        }
    }
}
