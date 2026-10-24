package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.7 Animations, Constant swing speed: every arm swing takes vanilla's 6 ticks, Haste or Mining Fatigue. Client world only. */
@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseSwingMixin189 {
    @Inject(method = "getArmSwingAnimationEnd", at = @At("HEAD"), cancellable = true)
    private void ladsConstantSwing(CallbackInfoReturnable<Integer> cir) {
        if (PvpVisuals.constantSwing && ((Entity) (Object) this).worldObj.isRemote) cir.setReturnValue(6);
    }
}
