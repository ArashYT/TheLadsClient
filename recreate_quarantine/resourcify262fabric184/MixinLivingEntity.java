package com.thelads.core.mixin.auto.resourcify262fabric184;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void onTick(CallbackInfo ci) {
        // Minimal safe injection point
    }
}
