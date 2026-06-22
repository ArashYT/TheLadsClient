/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.opengl.GlStateManager
 *  com.mojang.blaze3d.systems.RenderSystem
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.avoid_redundant_framebuffer_switching;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={RenderSystem.class})
public abstract class MixinRenderSystem {
    @Inject(method={"flipFrame"}, at={@At(value="HEAD")})
    private static void unbindFramebufferBeforeSwappingBuffers(CallbackInfo ci) {
        if (!com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast.isEnabled()) {
            return;
        }
        GlStateManager._glBindFramebuffer((int)36160, (int)0);
    }
}

