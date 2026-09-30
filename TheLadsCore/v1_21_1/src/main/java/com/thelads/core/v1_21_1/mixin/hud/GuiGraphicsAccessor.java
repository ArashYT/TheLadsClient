package com.thelads.core.v1_21_1.mixin.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A faded scope draws through the game's own buffer source, not a HUD batcher's (NativeAutohide.begin/end). */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    @Mutable @Accessor("bufferSource") void ladsSetBufferSource(MultiBufferSource.BufferSource source);
}
