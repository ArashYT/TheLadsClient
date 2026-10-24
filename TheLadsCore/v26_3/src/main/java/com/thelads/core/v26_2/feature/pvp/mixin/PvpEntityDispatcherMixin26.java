package com.thelads.core.v26_2.feature.pvp.mixin;

import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide Mob Deaths: a dying mob is culled like an entity off screen (never extracted, so nothing of it is drawn). */
@Mixin(EntityRenderDispatcher.class)
public class PvpEntityDispatcherMixin26 {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void lads$hideMobDeath(Entity entity, Frustum frustum, double camX, double camY, double camZ, float partial,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (PvpVisuals26.hidden(entity)) cir.setReturnValue(false);
    }
}
