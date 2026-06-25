package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.entity.layers.EquipmentLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentLayer.class)
public class MixinEquipmentLayerRenderer {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRender(net.minecraft.client.renderer.entity.RenderLayerParent<?> renderer, net.minecraft.world.entity.LivingEntity entitylivingbaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale, net.minecraft.client.renderer.MultiBufferSource bufferIn, int packedLightIn) {
        // Custom logic here
    }
}
