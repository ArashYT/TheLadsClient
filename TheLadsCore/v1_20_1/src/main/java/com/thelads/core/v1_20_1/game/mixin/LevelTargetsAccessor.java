package com.thelads.core.v1_20_1.game.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelTargetsAccessor {
    /** The entity outline target: it exists whenever the level renderer's post chains do, and shares their size. */
    @Accessor("entityTarget")
    RenderTarget ladsEntityTarget();
}
