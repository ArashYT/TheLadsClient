/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.core;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;
import com.thelads.core.features.alwayson.immediatelyfast.feature.core.ByteBufferBuilderPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={RenderSystem.class})
public abstract class MixinRenderSystem {
    @Inject(method={"initRenderer"}, at={@At(value="RETURN")})
    private static void initImmediatelyFast(CallbackInfo ci) {
        ImmediatelyFast.onRenderSystemInit();
    }

    @Inject(method={"flipFrame"}, at={@At(value="HEAD")})
    private static void endFrame(CallbackInfo ci) {
        ByteBufferBuilderPool.onEndFrame();
    }
}

