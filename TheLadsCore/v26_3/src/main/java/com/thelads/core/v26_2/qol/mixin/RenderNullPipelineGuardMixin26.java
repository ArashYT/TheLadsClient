package com.thelads.core.v26_2.qol.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two native/hard crashes from render types that cannot be drawn in the current pass (6 crash reports on 3 PCs, nvoglv64.dll NULL deref):
 * (1) the Vulkan backend binds withoutDepthPipeline = VK_NULL_HANDLE when a pipeline with a depth-stencil state is drawn into a pass
 * without a depth attachment (the outline pass), and the driver dereferences it; (2) TLib's tlib_particle has no OIT pipelines, so
 * drawFromBufferOit throws on the first frame with "Transparency: shader". Skip those draws, log each render type once.
 */
@Mixin(PreparedRenderType.class)
abstract class RenderNullPipelineGuardMixin26 {
    @Unique private static final Logger LADS_LOG = LoggerFactory.getLogger("Lads render guard");
    @Unique private static final Set<String> LADS_SKIPPED = ConcurrentHashMap.newKeySet();

    @Shadow @SuppressWarnings("unused") private String name;
    @Shadow @SuppressWarnings("unused") private OitPipelineSet oitPipelineSet;

    @Inject(method = "draw(Lnet/minecraft/client/renderer/StagedVertexBuffer$ExecuteInfo;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void lads$skipNullDepthPipeline(StagedVertexBuffer.ExecuteInfo info, RenderPass pass, RenderPipeline pipeline, CallbackInfo ci) {
        if (pass instanceof FrontendRenderPass frontend && pipeline != null && pipeline.getDepthStencilState() != null) {
            FrontendRenderPassAccessor26 access = (FrontendRenderPassAccessor26) frontend;
            if (!access.lads$hasDepthAttachment() && access.lads$backend().getClass().getName().contains(".vulkan.")) {
                ci.cancel();
                if (LADS_SKIPPED.add(name)) LADS_LOG.warn("[Lads] skipped {} (pipeline needs depth, render pass has no depth attachment; Vulkan would bind a null pipeline)", name);
            }
        }
    }

    @Inject(method = "drawFromBufferOit(Lnet/minecraft/client/renderer/StagedVertexBuffer$ExecuteInfo;Lnet/minecraft/client/renderer/oit/OitStage;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void lads$skipWithoutOit(StagedVertexBuffer.ExecuteInfo info, net.minecraft.client.renderer.oit.OitStage stage, RenderPass pass, CallbackInfo ci) {
        if (oitPipelineSet == null) {
            ci.cancel();
            if (LADS_SKIPPED.add(name)) LADS_LOG.warn("[Lads] skipped {} (render type has no OIT pipelines set up)", name);
        }
    }
}
