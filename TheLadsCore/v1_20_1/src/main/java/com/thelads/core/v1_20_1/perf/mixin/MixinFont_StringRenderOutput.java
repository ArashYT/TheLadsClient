package com.thelads.core.v1_20_1.perf.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reuses the glyph buffer while consecutive glyphs of one string share a render type. Off with {@code -Dlads.fontcache=false}. */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class MixinFont_StringRenderOutput {
    @Unique private RenderType ladsLastType;
    @Unique private VertexConsumer ladsLastBuffer;

    @Redirect(method = "accept", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer ladsGlyphBuffer(MultiBufferSource source, RenderType type) {
        if (type != ladsLastType || ladsLastBuffer == null) {
            ladsLastType = type;
            ladsLastBuffer = source.getBuffer(type);
        }
        return ladsLastBuffer;
    }
}
