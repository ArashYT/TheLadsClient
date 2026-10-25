package com.thelads.core.v1_20_1.mixin.social;

import com.thelads.core.v1_20_1.social.SocialBridge1201;
import java.net.InetAddress;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin to bind the integrated server socket to 127.0.0.1 (loopback only) when publishing in Friends/Invite scope.
 */
@Mixin(ServerConnectionListener.class)
public abstract class ServerConnectionListenerMixin {
    @ModifyVariable(method = "startTcpServerListener(Ljava/net/InetAddress;I)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private InetAddress ladsBindLoopback(InetAddress address) {
        if (SocialBridge1201.isLoopbackPublish()) {
            return InetAddress.getLoopbackAddress();
        }
        return address;
    }
}
