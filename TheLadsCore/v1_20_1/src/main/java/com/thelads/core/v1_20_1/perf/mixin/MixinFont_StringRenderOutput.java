package com.thelads.core.v1_20_1.perf.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Caches the active VertexConsumer for consecutive glyphs sharing the same font texture RenderType,
 * cutting hundreds of map lookups per string render pass.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class MixinFont_StringRenderOutput {

    @Unique private RenderType immediatelyFast$lastRenderType;
    @Unique private VertexConsumer immediatelyFast$lastConsumer;

    @Redirect(
        method = "accept",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
        ),
        require = 0
    )
    private VertexConsumer immediatelyFast$cacheGetBuffer(MultiBufferSource source, RenderType renderType) {
        if (this.immediatelyFast$lastRenderType == renderType && this.immediatelyFast$lastConsumer != null) {
            return this.immediatelyFast$lastConsumer;
        }
        this.immediatelyFast$lastRenderType = renderType;
        this.immediatelyFast$lastConsumer = source.getBuffer(renderType);
        return this.immediatelyFast$lastConsumer;
    }

    @Inject(method = "finish", at = @At("RETURN"), require = 0)
    private void immediatelyFast$clearCachedBuffer(int backgroundColor, float x, CallbackInfoReturnable<Float> cir) {
        this.immediatelyFast$lastRenderType = null;
        this.immediatelyFast$lastConsumer = null;
    }
}
