package com.thelads.core.v1_21_1.embedded.entityculling.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_21_1.embedded.entityculling.EntityCulling;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla's frustum/distance decision stands; an entity it would draw is additionally skipped when occluded. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private boolean lads$occlusionCull(boolean visible, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true, ordinal = 0) double cameraX,
                                       @Local(argsOnly = true, ordinal = 1) double cameraY, @Local(argsOnly = true, ordinal = 2) double cameraZ) {
        if (!visible || EntityCulling.shadowPass()) return visible;
        EntityCulling.camera(cameraX, cameraY, cameraZ);
        if (entity.noCulling) return true;
        return EntityCulling.drawEntity(entity);
    }
}
