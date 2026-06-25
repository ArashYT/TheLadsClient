package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class MixinLivingEntityRenderer {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRender(net.minecraft.world.entity.LivingEntity entity, float entityYaw, float partialTicks, net.minecraft.client.renderer.MultiBufferSource bufferIn, int packedLightIn) {
        // Custom logic here
    }
}
