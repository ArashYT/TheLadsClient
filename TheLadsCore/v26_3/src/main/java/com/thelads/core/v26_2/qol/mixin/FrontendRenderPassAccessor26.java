package com.thelads.core.v26_2.qol.mixin;

import com.mojang.renderpearl.backend.api.RenderPassBackend;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FrontendRenderPass.class)
public interface FrontendRenderPassAccessor26 {
    @Accessor("hasDepthAttachment") boolean lads$hasDepthAttachment();
    @Accessor("backend") RenderPassBackend lads$backend();
}
