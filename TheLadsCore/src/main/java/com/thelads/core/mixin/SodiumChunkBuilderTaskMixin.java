package com.thelads.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.client.benchmark.BenchmarkTracker;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap = false)
public class SodiumChunkBuilderTaskMixin {
    @Inject(method = "execute", at = @At("RETURN"))
    private void onExecuteReturn(CallbackInfoReturnable<?> cir) {
        BenchmarkTracker.incrementCompiledChunks();
    }
}
