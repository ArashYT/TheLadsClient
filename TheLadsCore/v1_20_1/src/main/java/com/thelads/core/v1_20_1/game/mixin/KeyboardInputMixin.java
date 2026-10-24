package com.thelads.core.v1_20_1.game.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
import com.thelads.core.v1_20_1.hud.chat.ChatTweaks1201;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Toggle Sprint &amp; Sneak: once per player tick, after vanilla read the keys. */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void lads$movement(boolean slowDown, float sneakSlowDown, CallbackInfo callback) {
        NativeFeatures1201.movement((KeyboardInput) (Object) this);
    }

    /** Inventory Move (gated): the walk and jump keys with a container screen open (ChatTweaks1201.moveKey). */
    @WrapOperation(method = "tick(ZF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"), require = 6)
    private boolean lads$inventoryMove(KeyMapping key, Operation<Boolean> original) {
        return original.call(key) || ChatTweaks1201.moveKey(key);
    }
}
