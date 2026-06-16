/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.MultiBufferSource$BufferSource
 *  net.minecraft.client.renderer.feature.FeatureRenderDispatcher
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.enhanced_batching;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={FeatureRenderDispatcher.class})
public abstract class MixinFeatureRenderDispatcher {
    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

    @Inject(method={"renderTranslucentFeatures"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer;renderTranslucent(Lnet/minecraft/client/renderer/SubmitNodeCollection;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/gui/Font;)V")})
    private void drawBatch1(CallbackInfo ci) {
        this.bufferSource.endLastBatch();
    }

    @Inject(method={"renderSolidFeatures"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/renderer/feature/ParticleFeatureRenderer;renderSolid(Lnet/minecraft/client/renderer/SubmitNodeCollection;)V")})
    private void drawBatch2(CallbackInfo ci) {
        this.bufferSource.endLastBatch();
    }
}

