package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.particle.ItemPickupParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemPickupParticle.class)
public class MixinItemPickupParticle_LegacyPickupPosition {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRender(net.minecraft.client.renderer.MultiBufferSource buffer, net.minecraft.util.Mth.PoseStack matrix) {
        // Custom logic here
    }
}
