package com.thelads.core.v1_20_1.qol.mixin;

import com.thelads.core.client.CreativeFlyBoost;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Abilities.class)
public class AbilitiesMixin1201 {
    @Shadow public boolean flying;

    @Inject(method = "getFlyingSpeed", at = @At("RETURN"), cancellable = true)
    private void lads$flyBoost(CallbackInfoReturnable<Float> cir) {
        if (this.flying && CreativeFlyBoost.isAllowed()) {
            cir.setReturnValue(cir.getReturnValueF() * CreativeFlyBoost.getMultiplier());
        }
    }
}
