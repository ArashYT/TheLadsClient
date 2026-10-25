package com.thelads.core.v1_8_9.qol.mixin;

import com.thelads.core.client.CreativeFlyBoost;
import net.minecraft.entity.player.PlayerCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerCapabilities.class)
public class PlayerCapabilitiesMixin189 {
    @Shadow public boolean isFlying;

    @Inject(method = "getFlySpeed", at = @At("RETURN"), cancellable = true)
    private void lads$flyBoost(CallbackInfoReturnable<Float> cir) {
        if (this.isFlying && CreativeFlyBoost.isAllowed()) {
            cir.setReturnValue(cir.getReturnValueF() * CreativeFlyBoost.getMultiplier());
        }
    }
}
