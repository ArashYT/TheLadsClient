package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Particle.class)
public class MixinParticle {
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void onTick(net.minecraft.client.renderer.MultiBufferSource buffer) {
        // Custom logic here
    }
}
