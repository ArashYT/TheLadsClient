package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.v1_20_1.feature.NativeFeatures1201;
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
}
