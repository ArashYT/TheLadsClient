package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.GlState189;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.9.13, without OptiFine (EarlyPlugin189): renderEntities turns the item lighting on before the entities and again before the
 * block entities, twice a frame (Forge renders entities in two passes), ~18 GL calls each time. Between the two only entities
 * render, which keep the camera's matrix; when the lighting is still on there, the second switch-on is skipped.
 */
@Mixin(RenderGlobal.class)
public abstract class EntityPassLightingMixin189 {
    @Redirect(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderHelper;enableStandardItemLighting()V", ordinal = 1), require = 0)
    private void lads$blockEntityLighting() {
        if (!GlState189.lighting()) RenderHelper.enableStandardItemLighting();
    }
}
