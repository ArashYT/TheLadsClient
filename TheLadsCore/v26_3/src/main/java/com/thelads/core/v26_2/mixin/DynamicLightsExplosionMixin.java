package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeDynamicLights;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Explosion flash dynamic lighting on 26.x: client-side explosion packets trigger a short-lived,
 * power-scaled dynamic light flash fading to darkness.
 */
@Mixin(ClientPacketListener.class)
abstract class DynamicLightsExplosionMixin {
    @Inject(method = "handleExplosion", at = @At("TAIL"), require = 1)
    private void lads$explosionFlash(ClientboundExplodePacket packet, CallbackInfo callback) {
        var center = packet.center();
        NativeDynamicLights.onExplosion(center.x(), center.y(), center.z(), packet.radius());
    }
}
