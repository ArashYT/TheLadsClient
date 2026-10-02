package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeOldAnimations;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class OldAnimationsCameraMixin {
    @Shadow private Entity entity;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;

    // 1.7 Animations' Instant sneak camera: 1.7's step replaces vanilla's half-way ease (rendering interpolates both).
    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;eyeHeight:F", opcode = Opcodes.PUTFIELD,
        shift = At.Shift.AFTER), require = 1)
    private void lads$instantSneak(CallbackInfo callback) {
        eyeHeight = NativeOldAnimations.eyeHeight(eyeHeightOld, entity.getEyeHeight(), eyeHeight);
    }
}
