// Adapted from Capes 1.5.10+1.21.11 by Cael (LGPL-2.1-only); modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.capes.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderDispatcher.class)
public interface AccessorEntityRenderManager {
    @Accessor("equipmentAssets")
    EquipmentAssetManager getEquipmentModelLoader();
}
