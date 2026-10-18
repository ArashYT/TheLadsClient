package com.thelads.core.v26_2.feature.food.mixin;

import com.thelads.core.v26_2.feature.food.NativeFood;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AppleSkin's server payloads without a registered receiver: read where vanilla would log them as unknown (client thread). */
@Mixin(ClientPacketListener.class)
abstract class AppleSkinPayloadMixin {
    @Inject(method = "handleUnknownCustomPayload", at = @At("HEAD"), cancellable = true)
    private void lads$appleSkin(CustomPacketPayload payload, CallbackInfo callback) {
        if (NativeFood.payload(payload)) callback.cancel();
    }
}
