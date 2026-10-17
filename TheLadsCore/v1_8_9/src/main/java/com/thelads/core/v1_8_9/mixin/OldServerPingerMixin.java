package com.thelads.core.v1_8_9.mixin;

import com.google.common.net.InetAddresses;
import com.thelads.core.v1_8_9.feature.SurvivalTweaks189;
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

    /** SurvivalTweaks Fast IP Ping (as 26.x's InetAddressPatcher): a numeric IP's address carries it as its host name, so nothing reverse-resolves it. */
    @Redirect(method = "ping", at = @At(value = "INVOKE", target = "Ljava/net/InetAddress;getByName(Ljava/lang/String;)Ljava/net/InetAddress;", remap = false), require = 1)
    private InetAddress ladsFastIpPing(String host) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(host);
        return SurvivalTweaks189.fastIpPing() && InetAddresses.isInetAddress(host) ? InetAddress.getByAddress(host, address.getAddress()) : address;
    }
}
