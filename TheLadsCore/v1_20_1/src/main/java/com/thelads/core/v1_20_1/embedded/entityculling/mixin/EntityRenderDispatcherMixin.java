package com.thelads.core.v1_20_1.embedded.entityculling.mixin;

import com.thelads.core.v1_20_1.embedded.entityculling.EntityCulling;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla's frustum/distance decision stands; an entity it would draw is additionally skipped when occluded. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void lads$occlusionCull(Entity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || EntityCulling.shadowPass()) return;
        EntityCulling.camera(cameraX, cameraY, cameraZ);
        if (!EntityCulling.drawEntity(entity)) cir.setReturnValue(false);
    }
}
