package com.thelads.core.mixin;

import com.thelads.core.modules.PingViewModule;
import com.thelads.core.config.ModuleManager;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handlePongResponse", at = @At("HEAD"), require = 0)
    private void onPongResponse(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        PingViewModule.onPong(packet.time());
    }
}
