// Adapted from Capes 1.5.10+1.21.11 by Cael (LGPL-2.1-only); modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_11.embedded.capes.mixin;

import com.thelads.core.v1_21_11.embedded.capes.render.PlaceholderEntity;
import com.thelads.core.v1_21_11.embedded.capes.render.PlaceholderEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderManager {

    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("HEAD"), cancellable = true)
    public <S extends EntityRenderState> void getPlaceholderRenderer(S state, CallbackInfoReturnable<EntityRenderer<?, ? super S>> cir) {
        if (state instanceof PlaceholderEntityRenderState) {
            cir.setReturnValue((EntityRenderer<?, ? super S>) PlaceholderEntity.getRenderer());
        }
    }
}
