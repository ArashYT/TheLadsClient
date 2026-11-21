package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ChunkPriority26;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smart Chunk Priority (SectionQueueMixin26): snapshots the look direction each time the builder gets the camera (main thread). */
@Mixin(SectionRenderDispatcher.class)
public abstract class SectionDispatcherMixin26 {
    @Inject(method = "setCameraPosition", at = @At("HEAD"), require = 1)
    private void lads$camera(Vec3 position, CallbackInfo ci) {
        ChunkPriority26.meshCamera();
    }
}
