package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render Tweaks, Entity Shadows off: no shadow under any entity. OptiFine patches Render: require = 0 keeps vanilla if it moves. */
@Mixin(Render.class)
public abstract class RenderShadowMixin189 {
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladsHideShadow(Entity entity, double x, double y, double z, float alpha, float partialTicks, CallbackInfo ci) {
        if (PvpVisuals.hideShadows) ci.cancel();
    }
}
