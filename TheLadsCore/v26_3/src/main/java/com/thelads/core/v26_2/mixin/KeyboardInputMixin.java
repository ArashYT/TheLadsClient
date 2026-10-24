package com.thelads.core.v26_2.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.feature.ChatTweaks26;
import com.thelads.core.v26_2.feature.NativeFeatures;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick()V", at = @At("TAIL"), require = 1)
    private void ladsMovement(CallbackInfo ci) { keyPresses = NativeFeatures.movement(keyPresses); }

    /** Inventory Move (gated): the walk and jump keys with a container screen open (ChatTweaks26.moveKey). */
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"), require = 7)
    private boolean ladsInventoryMove(KeyMapping key, Operation<Boolean> original) {
        return original.call(key) || ChatTweaks26.moveKey(key);
    }
}
