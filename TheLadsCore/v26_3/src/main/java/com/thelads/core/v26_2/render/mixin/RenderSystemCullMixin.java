package com.thelads.core.v26_2.render.mixin;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.thelads.core.v26_2.render.ItemCull26;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lads Flerovium (1.8.5): follows which projection is current, and its backup and restore (ItemCull26). */
@Mixin(RenderSystem.class)
public abstract class RenderSystemCullMixin {
    @Inject(method = "setProjectionMatrix", at = @At("HEAD"))
    private static void lads$projection(GpuBufferSlice projectionMatrixBuffer, ProjectionType type, CallbackInfo ci) {
        ItemCull26.projection(projectionMatrixBuffer);
    }

    @Inject(method = "backupProjectionMatrix", at = @At("HEAD"))
    private static void lads$backup(CallbackInfo ci) { ItemCull26.backup(); }

    @Inject(method = "restoreProjectionMatrix", at = @At("HEAD"))
    private static void lads$restore(CallbackInfo ci) { ItemCull26.restore(); }
}
