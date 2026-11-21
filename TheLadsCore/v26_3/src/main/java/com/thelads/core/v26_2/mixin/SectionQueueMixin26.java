package com.thelads.core.v26_2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v26_2.feature.ChunkPriority26;
import net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Smart Chunk Priority, mesh build order (a): the section build queue picks the task nearest the camera; in singleplayer/LAN a
 * section in front of the camera counts as nearer. The nearest sections are never outranked. Client-side order only.
 */
@Mixin(SectionTaskDynamicQueue.class)
public abstract class SectionQueueMixin26 {
    @WrapOperation(method = "poll", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D"), require = 1)
    private double lads$biased(BlockPos origin, Position camera, Operation<Double> original) {
        double dist = original.call(origin, camera);
        return ChunkPriority26.meshDistance(dist, origin.getX() + 8.0 - camera.x(), origin.getZ() + 8.0 - camera.z());
    }
}
