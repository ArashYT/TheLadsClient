package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Toggle Sprint: aiStep reads the Sprint key twice (double tap and held); both see what NativeFeatures1201.movement decided. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @WrapOperation(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"), require = 2)
    private boolean lads$sprintKey(KeyMapping key, Operation<Boolean> original) {
        return NativeFeatures1201.sprintKeyDown(key, original.call(key));
    }

    @Inject(method = "aiStep()V", at = @At("RETURN"))
    private void lads$movementDone(CallbackInfo callback) {
        NativeFeatures1201.movementDone();
    }
}
