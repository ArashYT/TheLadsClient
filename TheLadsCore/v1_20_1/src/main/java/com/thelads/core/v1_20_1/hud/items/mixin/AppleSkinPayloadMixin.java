package com.thelads.core.v1_20_1.hud.items.mixin;

import com.thelads.core.v1_20_1.hud.items.Food1201;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AppleSkin's server payloads without a registered receiver (HEAD: network thread, before the client-thread hand-off). */
@Mixin(ClientPacketListener.class)
abstract class AppleSkinPayloadMixin {
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void lads$appleSkin(ClientboundCustomPayloadPacket packet, CallbackInfo callback) {
        if (Food1201.payload(packet)) callback.cancel();
    }
}
