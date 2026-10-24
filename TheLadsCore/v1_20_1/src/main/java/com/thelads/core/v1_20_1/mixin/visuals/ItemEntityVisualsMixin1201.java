package com.thelads.core.v1_20_1.mixin.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.modules.ItemDespawnModule;
import com.thelads.core.v1_20_1.feature.PvpVisuals1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Loot Beams and Item Despawn on 1.20.1's dropped items. Item Physics (priority 900) draws the item itself and cancels this method,
 * so these run first (priority 800): the beam at the entity's origin, then the despawn blink skips the item on its off frames.
 */
@Mixin(value = ItemEntityRenderer.class, priority = 800)
public abstract class ItemEntityVisualsMixin1201 {
    @Inject(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"), cancellable = true)
    private void ladsBeamAndBlink(ItemEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        PvpVisuals1201.beam(entity.getItem(), pose, buffers);
        if (ItemDespawnModule.hidden(entity.getAge(), ItemDespawnModule.LIFETIME)) ci.cancel();
    }
}
