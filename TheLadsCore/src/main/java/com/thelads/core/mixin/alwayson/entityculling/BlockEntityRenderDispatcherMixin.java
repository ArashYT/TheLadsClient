/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher
 *  net.minecraft.client.renderer.blockentity.BlockEntityRenderer
 *  net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
 *  net.minecraft.client.renderer.culling.Frustum
 *  net.minecraft.client.renderer.feature.ModelFeatureRenderer$CrumblingOverlay
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin.alwayson.entityculling;

import com.thelads.core.features.alwayson.entityculling.EntityCullingModBase;
import com.thelads.core.mixin.alwayson.entityculling.access.Cullable;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={BlockEntityRenderDispatcher.class})
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method={"tryExtractRenderState"}, at={@At(value="HEAD")}, cancellable=true)
    public void tryExtractRenderState(BlockEntity blockEntity, float f, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, CallbackInfoReturnable<BlockEntityRenderState> info) {
        if (EntityCullingModBase.instance.config.skipBlockEntityCulling) {
            return;
        }
        BlockEntityRenderer blockEntityRenderer = this.getRenderer(blockEntity);
        if (blockEntityRenderer == null) {
            return;
        }
        Frustum frustum = EntityCullingModBase.instance.frustum;
        if (blockEntityRenderer.shouldRenderOffScreen()) {
            ++EntityCullingModBase.instance.renderedBlockEntities;
            return;
        }
        if (EntityCullingModBase.instance.config.blockEntityFrustumCulling && frustum != null && !frustum.isVisible(EntityCullingModBase.instance.setupAABB(blockEntity, blockEntity.getBlockPos()))) {
            ++EntityCullingModBase.instance.skippedBlockEntities;
            info.setReturnValue(null);
            if (EntityCullingModBase.instance.debugCollector.isRunning()) {
                EntityCullingModBase.instance.debugCollector.addBlockEntity(blockEntity, false);
            }
            return;
        }
        if (blockEntity instanceof Cullable) {
            Cullable cullable = (Cullable)blockEntity;
            if (!cullable.isForcedVisible() && cullable.isCulled()) {
                ++EntityCullingModBase.instance.skippedBlockEntities;
                info.setReturnValue(null);
                if (EntityCullingModBase.instance.debugCollector.isRunning()) {
                    EntityCullingModBase.instance.debugCollector.addBlockEntity(blockEntity, false);
                }
                return;
            }
            ++EntityCullingModBase.instance.renderedBlockEntities;
            cullable.setOutOfCamera(false);
            if (EntityCullingModBase.instance.debugCollector.isRunning()) {
                EntityCullingModBase.instance.debugCollector.addBlockEntity(blockEntity, true);
            }
        }
    }

    @Shadow
    public abstract <E extends BlockEntity> BlockEntityRenderer getRenderer(E var1);
}

