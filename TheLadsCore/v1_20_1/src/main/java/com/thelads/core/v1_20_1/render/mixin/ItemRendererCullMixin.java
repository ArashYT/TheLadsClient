package com.thelads.core.v1_20_1.render.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.v1_20_1.render.ItemCull1201;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lads Flerovium (1.8.5): ItemCull1201 wraps the model of every renderModelLists call made by ItemRenderer.render. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererCullMixin {
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At("HEAD"))
    private void lads$begin(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        ItemCull1201.begin(context);
        com.thelads.core.perf.RenderQa.itemStart();
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At("RETURN"))
    private void lads$end(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        com.thelads.core.perf.RenderQa.itemEnd(); // QA timing only (counts while the render capture measures)
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"),
        index = 0)
    private BakedModel lads$cull(BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose, VertexConsumer consumer) {
        return ItemCull1201.wrap(model, pose);
    }

    @Inject(method = "getFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"))
    private static void lads$foil(MultiBufferSource buffers, RenderType type, boolean item, boolean glint, CallbackInfoReturnable<VertexConsumer> cir) {
        ItemCull1201.renderType(type);
    }

    @Inject(method = "getFoilBufferDirect(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"))
    private static void lads$foilDirect(MultiBufferSource buffers, RenderType type, boolean item, boolean glint, CallbackInfoReturnable<VertexConsumer> cir) {
        ItemCull1201.renderType(type);
    }

    @Inject(method = "getCompassFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"))
    private static void lads$compassFoil(MultiBufferSource buffers, RenderType type, PoseStack.Pose pose, CallbackInfoReturnable<VertexConsumer> cir) {
        ItemCull1201.renderType(type);
    }

    @Inject(method = "getCompassFoilBufferDirect(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"))
    private static void lads$compassFoilDirect(MultiBufferSource buffers, RenderType type, PoseStack.Pose pose, CallbackInfoReturnable<VertexConsumer> cir) {
        ItemCull1201.renderType(type);
    }
}
