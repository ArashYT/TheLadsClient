package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.perf.Perf189Settings;
import com.thelads.core.v1_8_9.perf.EntityInstancing189;
import java.util.Set;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Parity fix for MC-63020 and MC-70850 (invisible chunks / frustum culling false negatives),
 * and pass lifecycle hooks for GPU entity instancing.
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalCullingMixin189 {
    @Redirect(method = "setupTerrain", at = @At(value = "INVOKE", target = "Ljava/util/Set;isEmpty()Z", ordinal = 1), require = 0)
    private boolean lads$chunkCullingFix(Set<?> set) {
        if (Perf189Settings.chunkCullingFix) {
            return false;
        }
        return set.isEmpty();
    }

    @Inject(method = "renderEntities", at = @At("HEAD"), require = 0)
    private void lads$beginEntityInstancing(Entity renderViewEntity, ICamera camera, float partialTicks, CallbackInfo ci) {
        EntityInstancing189.beginPass();
    }

    @Inject(method = "renderEntities", at = @At("TAIL"), require = 0)
    private void lads$flushEntityInstancing(Entity renderViewEntity, ICamera camera, float partialTicks, CallbackInfo ci) {
        EntityInstancing189.flush();
    }
}
