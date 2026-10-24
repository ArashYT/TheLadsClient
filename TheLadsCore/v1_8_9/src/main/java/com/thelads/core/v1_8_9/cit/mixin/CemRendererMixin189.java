package com.thelads.core.v1_8_9.cit.mixin;

import com.thelads.core.client.cem.CemLoader;
import com.thelads.core.v1_8_9.cit.Cem189;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Entity Models (CEM) living entity renderer hook for 1.8.9 on Ionium.
 */
@Mixin(RendererLivingEntity.class)
public abstract class CemRendererMixin189 {
    @Inject(method = "renderModel", at = @At("HEAD"), require = 0)
    private void lads$cemModelRender(EntityLivingBase entity, float p_77036_2_, float p_77036_3_,
                                     float p_77036_4_, float p_77036_5_, float p_77036_6_, float scaleFactor, CallbackInfo ci) {
        if (entity != null) {
            String entityName = entity.getName();
            CemLoader.Model cem = Cem189.getModel(entityName);
            if (cem != null) {
                // CEM model active in living entity renderer
            }
        }
    }
}
