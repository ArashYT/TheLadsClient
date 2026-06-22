/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.blockentity.BlockEntityRenderer
 *  net.minecraft.core.Vec3i
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.phys.Vec3
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 */
package com.thelads.core.mixin.alwayson.farblockentityrendering;

import com.thelads.core.features.farblockentityrendering.ConfigManager;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value={BlockEntityRenderer.class})
public interface BlockEntityRendererMixin<T extends BlockEntity> {
    @Overwrite
    default public boolean shouldRender(T blockEntity, Vec3 cameraPos) {
        double dist = Vec3.atCenterOf((Vec3i)blockEntity.getBlockPos()).distanceToSqr(cameraPos);
        return dist < ConfigManager.getBlockEntityRenderDistanceSquared();
    }
}

