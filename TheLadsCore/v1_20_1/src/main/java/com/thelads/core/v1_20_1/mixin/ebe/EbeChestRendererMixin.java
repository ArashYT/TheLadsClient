package com.thelads.core.v1_20_1.mixin.ebe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.ebe.BakedBlockEntities;
import com.thelads.core.client.hypixel.OpenedChestHighlight;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dlads.ebe.xmas=true|false forces the Christmas chest texture for the vanilla renderer too (pixel-diff QA). */
@Mixin(ChestRenderer.class)
public abstract class EbeChestRendererMixin {
    @Shadow private boolean xmasTextures;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void lads$ebeForcedXmas(BlockEntityRendererProvider.Context context, CallbackInfo ci) {
        if (BakedBlockEntities.christmasForced()) xmasTextures = BakedBlockEntities.christmas();
    }

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"),
            require = 0)
    private void lads$highlightOpenedChest(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, CallbackInfo ci) {
        if (blockEntity != null && blockEntity.getBlockPos() != null && OpenedChestHighlight.isOpened(blockEntity.getBlockPos().getX(), blockEntity.getBlockPos().getY(), blockEntity.getBlockPos().getZ())) {
            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
            LevelRenderer.renderLineBox(poseStack, lineConsumer, 0.0625, 0.0, 0.0625, 0.9375, 0.875, 0.9375, 0.0f, 1.0f, 0.0f, 0.8f);
        }
    }
}
