package com.thelads.smp.features.arise.nb2.mixin;

import com.thelads.smp.features.arise.nb2.TooManyEntitiesManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to EntityRenderDispatcher to limit entity rendering density per chunk cell (Too Many Entities).
 */
@Environment(EnvType.CLIENT)
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @Inject(
            method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private <E extends Entity> void onShouldRender(E entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
        if (TooManyEntitiesManager.shouldSuppress(entity)) {
            cir.setReturnValue(false);
        }
    }
}
