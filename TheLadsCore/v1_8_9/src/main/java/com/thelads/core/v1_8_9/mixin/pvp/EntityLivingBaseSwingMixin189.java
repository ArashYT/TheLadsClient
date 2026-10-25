package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.7 Animations, Constant swing speed & haste slider: arm swing duration based on haste influence. Client world only. */
@Mixin(EntityLivingBase.class)
public abstract class EntityLivingBaseSwingMixin189 {
    @Inject(method = "getArmSwingAnimationEnd", at = @At("HEAD"), cancellable = true)
    private void ladsConstantSwing(CallbackInfoReturnable<Integer> cir) {
        EntityLivingBase living = (EntityLivingBase) (Object) this;
        if (living.worldObj != null && living.worldObj.isRemote) {
            if (PvpVisuals.constantSwing) {
                cir.setReturnValue(6);
            } else if (PvpVisuals.swingHasteInfluence < 100) {
                int haste = living.isPotionActive(Potion.digSpeed) ? 1 + living.getActivePotionEffect(Potion.digSpeed).getAmplifier() : 0;
                int fatigue = living.isPotionActive(Potion.digSlowdown) ? 1 + living.getActivePotionEffect(Potion.digSlowdown).getAmplifier() : 0;
                cir.setReturnValue(PvpVisuals.getSwingDuration(haste, fatigue, 6));
            }
        }
    }
}
