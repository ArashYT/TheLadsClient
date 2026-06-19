package com.thelads.core.mixin.auto.farblockentityrendering21;

import com.thelads.core.features.auto.farblockentityrendering.ConfigManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Recreation of FarBlockEntityRendering's BlockEntityRendererMixin (by IlyRac).
 * Overwrites the default {@code shouldRender} so block entities render out to a
 * configurable distance instead of the vanilla 64-block limit.
 */
@Mixin(net.minecraft.client.renderer.blockentity.BlockEntityRenderer.class)
public interface BlockEntityRendererMixin<T extends BlockEntity> {

    /**
     * @author The Lads Client (recreated from IlyRac's FarBlockEntityRendering)
     * @reason Extend block-entity render distance beyond the vanilla 64 blocks.
     */
    @Overwrite
    default boolean shouldRender(T blockEntity, Vec3 cameraPos) {
        double dist = Vec3.atCenterOf(blockEntity.getBlockPos()).distanceToSqr(cameraPos);
        return dist < ConfigManager.getBlockEntityRenderDistanceSquared();
    }
}
