package com.thelads.smp.features.arise.nb3.mixin;

import com.thelads.smp.features.arise.nb3.immersiveui.ImmersiveUIState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin into Camera to apply subtle directional screen shake on taking damage.
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float ladssmp$applyScreenShakeYRot(float yRot) {
        float shake = ImmersiveUIState.getShakeOffset();
        return yRot + shake;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float ladssmp$applyScreenShakeXRot(float xRot) {
        float shake = ImmersiveUIState.getShakeOffset();
        return xRot + shake;
    }
}
