package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.perf.EntityInstancing189;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts living entity rendering in RenderManager to record instance draw data
 * into the GPU instanced draw buffer pipeline when instancing is active.
 */
@Mixin(RenderManager.class)
public abstract class RenderManagerInstancingMixin189 {
    @Inject(method = "doRenderEntity", at = @At("HEAD"), require = 0)
    private void lads$instancedLivingEntity(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean hideDebugBox, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof EntityLivingBase && EntityInstancing189.isAvailable()) {
            EntityInstancing189.recordLivingEntity((EntityLivingBase) entity, x, y, z, yaw, partialTicks);
        }
    }
}
