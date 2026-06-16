/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.VertexConsumer
 *  net.minecraft.client.renderer.MultiBufferSource
 *  net.minecraft.client.renderer.rendertype.RenderType
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.fast_text_lookup;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets={"net.minecraft.client.gui.Font$GlyphVisitor$1"})
public abstract class MixinFont_GlyphVisitor {
    @Unique
    private RenderType immediatelyFast$lastRenderType;
    @Unique
    private VertexConsumer immediatelyFast$lastVertexConsumer;

    @Redirect(method={"render"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer reduceGetBufferCalls(MultiBufferSource instance, RenderType renderType) {
        if (this.immediatelyFast$lastRenderType == renderType) {
            return this.immediatelyFast$lastVertexConsumer;
        }
        this.immediatelyFast$lastRenderType = renderType;
        this.immediatelyFast$lastVertexConsumer = instance.getBuffer(renderType);
        return this.immediatelyFast$lastVertexConsumer;
    }
}

