package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class MixinHumanoidModel {
    @Inject(method = "setupAnim", at = @At("HEAD"), require = 0)
    private void onSetupAnim(net.minecraft.world.entity.LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Custom logic here
    }
}
