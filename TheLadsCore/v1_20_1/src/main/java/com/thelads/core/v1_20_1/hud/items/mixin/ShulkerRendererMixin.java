package com.thelads.core.v1_20_1.hud.items.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.hud.items.ShulkerContents1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Placed boxes (the item-form renderer's box has no level and is skipped): the world icon after the box model. */
@Mixin(ShulkerBoxRenderer.class)
abstract class ShulkerRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/ShulkerBoxBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At("TAIL"))
    private void lads$icon(ShulkerBoxBlockEntity box, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, CallbackInfo callback) {
        ShulkerContents1201.renderIcon(box, partialTick, pose, buffers, light);
    }
}
