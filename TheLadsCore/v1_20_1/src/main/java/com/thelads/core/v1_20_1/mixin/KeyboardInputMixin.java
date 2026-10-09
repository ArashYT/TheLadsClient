package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {
    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void ladsSneakTick(boolean slowDown, float sneakSlowDown, CallbackInfo ci) {
        if (NativeFeatures1201.isSneakToggled()) {
            this.shiftKeyDown = true;
            this.leftImpulse *= sneakSlowDown;
            this.forwardImpulse *= sneakSlowDown;
        }
    }
}
