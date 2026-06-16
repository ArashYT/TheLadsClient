/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.llamalad7.mixinextras.injector.v2.WrapWithCondition
 *  com.mojang.blaze3d.opengl.GlCommandEncoder
 *  com.mojang.blaze3d.opengl.GlStateManager
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.avoid_redundant_framebuffer_switching;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={GlCommandEncoder.class})
public abstract class MixinGlCommandEncoder {
    @WrapWithCondition(method={"finishRenderPass"}, at={@At(value="INVOKE", target="Lcom/mojang/blaze3d/opengl/GlStateManager;_glBindFramebuffer(II)V")})
    private boolean dontUnbindFramebuffer(int target, int framebuffer) {
        return false;
    }

    @Inject(method={"presentTexture"}, at={@At(value="HEAD")})
    private void unbindFramebufferBeforePresenting(CallbackInfo ci) {
        GlStateManager._glBindFramebuffer((int)36160, (int)0);
    }
}

