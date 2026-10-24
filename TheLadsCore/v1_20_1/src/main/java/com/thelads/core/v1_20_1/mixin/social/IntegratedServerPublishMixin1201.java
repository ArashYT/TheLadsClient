package com.thelads.core.v1_20_1.mixin.social;

import com.thelads.core.v1_20_1.social.SocialBridge1201;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Friends/Invite hosting never starts the LAN pinger: decided inside publishServer itself (the flag is set around that call), so no
 * pinger thread can ever multicast the world to the LAN (as 1.8.9 and 26.3 do).
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerPublishMixin1201 {
    @Redirect(method = "publishServer(Lnet/minecraft/world/level/GameType;ZI)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/LanServerPinger;start()V"))
    private void ladsNoLanPingForFriends(LanServerPinger pinger) {
        if (!SocialBridge1201.isSuppressLanPinger()) pinger.start();
    }
}
