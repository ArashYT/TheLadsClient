package com.thelads.core.v1_20_1.game.misc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.game.misc.NativeItemPhysics1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Physics owns a dropped item's placement while it is on: vanilla's bob and spin are skipped, the item lies, tumbles and floats.
 * Runs before other HEAD hooks of the same method (priority 900) so a 1.7 Animations icon can check NativeItemPhysics1201.renders().
 */
@Mixin(value = ItemEntityRenderer.class, priority = 900)
public abstract class ItemPhysicsRenderMixin extends EntityRenderer<ItemEntity> {
    @Shadow @Final private ItemRenderer itemRenderer;

    private ItemPhysicsRenderMixin(EntityRendererProvider.Context context) { super(context); }

    @Inject(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$itemPhysics(ItemEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (!NativeItemPhysics1201.render(entity, partial, pose, buffers, light, itemRenderer)) return;
        if (shouldShowName(entity)) renderNameTag(entity, entity.getDisplayName(), pose, buffers, light); // what EntityRenderer.render does
        callback.cancel();
    }
}
