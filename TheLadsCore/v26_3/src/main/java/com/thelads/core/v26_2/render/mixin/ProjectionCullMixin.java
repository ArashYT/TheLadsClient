package com.thelads.core.v26_2.render.mixin;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.thelads.core.v26_2.render.ItemCull26;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lads Flerovium (1.8.5): remembers the CPU copy of every projection matrix the game uploads (ItemCull26). */
@Mixin(ProjectionMatrixBuffer.class)
public abstract class ProjectionCullMixin {
    @Shadow @Final private GpuBuffer buffer;

    @Inject(method = "writeBuffer", at = @At("HEAD"))
    private void lads$remember(Matrix4f projectionMatrix, CallbackInfoReturnable<?> cir) {
        ItemCull26.uploaded(buffer, projectionMatrix);
    }
}
