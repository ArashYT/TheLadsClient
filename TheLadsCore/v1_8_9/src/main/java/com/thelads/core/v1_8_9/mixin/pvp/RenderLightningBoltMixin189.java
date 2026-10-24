package com.thelads.core.v1_8_9.mixin.pvp;

import com.thelads.core.client.PvpVisuals;
import net.minecraft.client.renderer.entity.RenderLightningBolt;
import net.minecraft.entity.effect.EntityLightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render Tweaks, Hide Lightning: the bolt is not drawn (its sound and sky flash stay). */
@Mixin(RenderLightningBolt.class)
public abstract class RenderLightningBoltMixin189 {
    @Inject(method = "doRender(Lnet/minecraft/entity/effect/EntityLightningBolt;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void ladsHideLightning(EntityLightningBolt bolt, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        if (PvpVisuals.hideLightning) ci.cancel();
    }
}
