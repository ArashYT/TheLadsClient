package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.ServerDnsCache189;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Resolves the target server's InetAddress using ServerDnsCache189, hitting
 * pre-resolved or ping-cached DNS entries to eliminate connection delay.
 */
@Mixin(targets = "net.minecraft.client.multiplayer.GuiConnecting$1")
public abstract class GuiConnectingThreadMixin {
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Ljava/net/InetAddress;getByName(Ljava/lang/String;)Ljava/net/InetAddress;"), require = 1)
    private InetAddress ladsResolveDns(String host) throws UnknownHostException {
        return ServerDnsCache189.resolve(host);
    }
}
