package com.thelads.core.mixin.auto.retromod110rc1262;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public class MixinLevel {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void tick(CallbackInfo ci) {
        // Minimal safe injection
    }
}
