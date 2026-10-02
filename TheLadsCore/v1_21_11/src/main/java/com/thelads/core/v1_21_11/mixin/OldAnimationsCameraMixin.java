package com.thelads.core.v1_21_11.mixin;

import com.thelads.core.v1_21_11.feature.NativeOldAnimations;
import net.minecraft.client.Camera;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Instant sneak camera: tick's eased eye height becomes 1.7's (the fields are widened in theladscore.accesswidener). */
@Mixin(Camera.class)
public class OldAnimationsCameraMixin {
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;eyeHeight:F", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
        require = 1)
    private void lads$instantSneak(CallbackInfo ci) {
        Camera camera = (Camera) (Object) this;
        camera.eyeHeight = NativeOldAnimations.eyeHeight(camera.eyeHeightOld, camera.eyeHeight, camera.entity());
    }
}
