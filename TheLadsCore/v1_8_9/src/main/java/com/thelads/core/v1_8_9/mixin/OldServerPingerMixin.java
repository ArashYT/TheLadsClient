package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.ServerDnsCache189;
import java.net.InetAddress;
import java.net.UnknownHostException;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OldServerPinger.class)
public abstract class OldServerPingerMixin {
    @Inject(method = "tryCompatibilityPing", at = @At("HEAD"), cancellable = true)
    private void ladsSuppressLegacyPing(ServerData server, CallbackInfo ci) {
        ci.cancel();
    }

    @Redirect(method = "ping", at = @At(value = "INVOKE", target = "Ljava/net/InetAddress;getByName(Ljava/lang/String;)Ljava/net/InetAddress;"), require = 1)
    private InetAddress ladsResolvePingDns(String host) throws UnknownHostException {
        return ServerDnsCache189.resolve(host);
    }
}
