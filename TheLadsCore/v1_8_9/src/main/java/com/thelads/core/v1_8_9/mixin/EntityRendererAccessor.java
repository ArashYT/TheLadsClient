package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** QA (Probe160): the FOV EntityRenderer computes, OptiFine's zoom and Forge's FOVModifier event included. Fullbright: the lightmap rebuild flag. */
@Mixin(EntityRenderer.class)
public interface EntityRendererAccessor {
    @Invoker("getFOVModifier") float ladsFov(float partialTicks, boolean useFOVSetting);
    @Accessor("lightmapUpdateNeeded") void ladsLightmapDirty(boolean dirty);
}
