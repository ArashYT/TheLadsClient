// Adapted from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only); modified by The Lads: repackaged into Lads Core.
package com.thelads.core.v26_2.embedded.capes.mixin;

import com.thelads.core.v26_2.embedded.capes.render.PlaceholderEntity;
import com.thelads.core.v26_2.embedded.capes.render.PlaceholderEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    public <S extends EntityRenderState> void getPlaceholderRenderer(S state, CallbackInfoReturnable<EntityRenderer<?, ? super S>> cir) {
        if (state instanceof PlaceholderEntityRenderState) {
            cir.setReturnValue((EntityRenderer<?, ? super S>) PlaceholderEntity.getRenderer());
        }
    }
}
