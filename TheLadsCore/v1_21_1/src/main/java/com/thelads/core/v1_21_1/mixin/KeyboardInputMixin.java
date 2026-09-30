package com.thelads.core.v1_21_1.mixin;
import com.thelads.core.v1_21_1.feature.NativeFeatures;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {
    @Inject(method = "tick(ZF)V", at = @At("TAIL"), require = 1)
    private void ladsMovement(boolean slow, float slowFactor, CallbackInfo ci) { NativeFeatures.movement(this); }
}
