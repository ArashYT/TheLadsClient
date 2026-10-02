// Adapted from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes.mixin;

import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderDispatcher.class)
public interface AccessorEntityRenderDispatcher {
    @Accessor
    EquipmentAssetManager getEquipmentAssets();

    @Accessor
    BlockModelResolver getBlockModelResolver();
}
