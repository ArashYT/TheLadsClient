package com.thelads.core.v1_8_9.world.mixin;

import com.thelads.core.v1_8_9.perf.StarlightLightingEngine189;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ChunkProviderClient lighting mixin: marks chunks light populated asynchronously
 * on chunk arrival to avoid render-thread light initialization freezes.
 */
@Mixin(ChunkProviderClient.class)
public abstract class ChunkProviderClientLightingMixin189 {
    @Inject(method = "loadChunk", at = @At("RETURN"), require = 0)
    private void lads$starlightChunkLoad(int chunkX, int chunkZ, CallbackInfoReturnable<Chunk> cir) {
        Chunk chunk = cir.getReturnValue();
        if (chunk != null) {
            StarlightLightingEngine189.onChunkLoaded(chunk);
        }
    }
}
