package com.thelads.core.v1_20_1.lod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.perf.PlayerLod;
import com.thelads.core.v1_20_1.lod.PlayerLod1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player LOD: head items beyond the item distance. */
@Mixin(CustomHeadLayer.class)
abstract class PlayerLodHeadItemMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void lads$lod(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        if (PlayerLod1201.has(PlayerLod.ITEMS)) ci.cancel();
    }
}
