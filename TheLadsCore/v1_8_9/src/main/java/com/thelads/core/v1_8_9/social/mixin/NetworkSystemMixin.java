package com.thelads.core.v1_8_9.social.mixin;

import com.thelads.core.v1_8_9.social.SocialBridge189;
import java.net.InetAddress;
import net.minecraft.network.NetworkSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Enforces loopback binding (127.0.0.1) when publishing integrated server to Friends/Invite scope.
 */
@Mixin(NetworkSystem.class)
public abstract class NetworkSystemMixin {
    @ModifyVariable(method = "addLanEndpoint", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1)
    private InetAddress ladsEnforceLoopback(InetAddress address) {
        if (SocialBridge189.isLoopbackOnly() && address == null) {
            return InetAddress.getLoopbackAddress();
        }
        return address;
    }
}
