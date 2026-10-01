package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA only (Probe145): how far the held item is raised. */
@Mixin(ItemRenderer.class)
public interface ItemRendererAccessor {
    @Accessor float getEquippedProgress();
}
