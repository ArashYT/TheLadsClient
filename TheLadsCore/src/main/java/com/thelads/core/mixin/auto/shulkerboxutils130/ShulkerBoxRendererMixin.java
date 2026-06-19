package com.thelads.core.mixin.auto.shulkerboxutils130;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.core.features.auto.shulkerboxutils.ShulkerBoxUtilsCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShulkerBoxRenderer.class)
public class ShulkerBoxRendererMixin {
    @Unique
    private ItemModelResolver thelads$resolver;

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/blockentity/BlockEntityRendererProvider$Context;)V", at = @At("TAIL"), require = 0)
    private void thelads$captureResolver(BlockEntityRendererProvider.Context context, CallbackInfo ci) {
        this.thelads$resolver = context.itemModelResolver();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/ShulkerBoxBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/ShulkerBoxRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("TAIL"), require = 0)
    private void thelads$cacheFirstItem(ShulkerBoxBlockEntity blockEntity, ShulkerBoxRenderState renderState, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
        BlockPos pos = blockEntity.getBlockPos().immutable();
        boolean hasAny = thelads$hasAnyItem((Container) blockEntity);
        if (hasAny && !ShulkerBoxUtilsCache.SCREEN_AUTHORITATIVE.contains(pos)) {
            ItemStack firstItem = thelads$getFirstItem((Container) blockEntity);
            ShulkerBoxUtilsCache.ITEMS.put(pos, firstItem.copy());
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/ShulkerBoxRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"), require = 0)
    private void thelads$renderIcon(ShulkerBoxRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (this.thelads$resolver == null) {
            return;
        }
        ItemStack firstItem = ShulkerBoxUtilsCache.ITEMS.get(renderState.blockPos);
        if (firstItem == null || firstItem.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ItemStackRenderState itemRenderState = new ItemStackRenderState();
        this.thelads$resolver.updateForTopItem(itemRenderState, firstItem, ItemDisplayContext.FIXED, level, null, 0);
        if (itemRenderState.isEmpty()) {
            return;
        }
        Direction dir = renderState.direction != null ? renderState.direction : Direction.UP;
        poseStack.pushPose();
        thelads$applyFaceTransform(poseStack, dir, renderState.progress);
        ((ItemStackRenderStateAccessor) (Object) itemRenderState).invokeSubmit(poseStack, submitNodeCollector, renderState.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    @Unique
    private static ItemStack thelads$getFirstItem(Container container) {
        for (int i = 0; i < container.getContainerSize(); ++i) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    @Unique
    private static boolean thelads$hasAnyItem(Container container) {
        for (int i = 0; i < container.getContainerSize(); ++i) {
            if (!container.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static void thelads$applyFaceTransform(PoseStack poseStack, Direction dir, float progress) {
        double o = 0.01;
        double lid = (double) progress * 0.5;
        switch (dir) {
            case UP: {
                poseStack.translate(0.5, 1.0 + lid + o, 0.5);
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
                break;
            }
            case DOWN: {
                poseStack.translate(0.5, -lid - o, 0.5);
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
                break;
            }
            case NORTH: {
                poseStack.translate(0.5, 0.5, -lid - o);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
                break;
            }
            case SOUTH: {
                poseStack.translate(0.5, 0.5, 1.0 + lid + o);
                break;
            }
            case EAST: {
                poseStack.translate(1.0 + lid + o, 0.5, 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
                break;
            }
            case WEST: {
                poseStack.translate(-lid - o, 0.5, 0.5);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0f));
                break;
            }
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
        poseStack.scale(0.5f, 0.5f, 0.5f);
    }
}
