package com.thelads.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.client.benchmark.BenchmarkTracker;

@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection$CompileTask")
public class RebuildTaskMixin {
    @Inject(method = "doTask", at = @At("RETURN"))
    private void onDoTaskReturn(CallbackInfoReturnable<?> cir) {
        BenchmarkTracker.incrementCompiledChunks();
    }
}
