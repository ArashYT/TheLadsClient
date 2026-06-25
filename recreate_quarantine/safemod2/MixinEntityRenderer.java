package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRender(net.minecraft.world.entity.Entity entity, float partialTicks, net.minecraft.client.renderer.MultiBufferSource bufferIn, int packedLightIn) {
        // Custom logic here
    }
}
