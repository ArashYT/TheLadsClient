package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.social.P2PHostState26;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.net.InetAddress;

@Mixin(ServerConnectionListener.class)
public abstract class ServerConnectionListenerMixin {
    @ModifyVariable(
        method = "startTcpServerListener(Ljava/net/InetAddress;I)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 1
    )
    private InetAddress ladsBindLoopback(InetAddress address) {
        if (P2PHostState26.isLoopbackScope()) {
            return InetAddress.getLoopbackAddress();
        }
        return address;
    }
}
