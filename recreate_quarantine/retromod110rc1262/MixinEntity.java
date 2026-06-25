package com.thelads.core.mixin.auto.retromod110rc1262;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void tick(CallbackInfo ci) {
        // Minimal safe injection
    }
}
