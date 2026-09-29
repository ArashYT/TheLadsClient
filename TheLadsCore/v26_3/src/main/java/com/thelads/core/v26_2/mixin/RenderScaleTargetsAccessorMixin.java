package com.thelads.core.v26_2.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The public outline getter is a transient frame-graph handle, unavailable between frames. */
@Mixin(LevelRenderer.class)
public interface RenderScaleTargetsAccessorMixin {
    @Accessor("entityOutlineTarget")
    RenderTarget lads$persistentOutlineTarget();
}
