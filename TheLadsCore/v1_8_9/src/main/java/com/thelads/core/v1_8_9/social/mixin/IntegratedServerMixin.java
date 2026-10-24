package com.thelads.core.v1_8_9.social.mixin;

import com.thelads.core.v1_8_9.social.SocialBridge189;
import net.minecraft.client.multiplayer.ThreadLanServerPing;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses LAN server broadcast ping when hosting on loopback for Friends/Invite scope.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Redirect(method = "shareToLAN", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ThreadLanServerPing;start()V"), require = 1)
    private void ladsSuppressLanPing(ThreadLanServerPing ping) {
        if (!SocialBridge189.isLoopbackOnly()) {
            ping.start();
        }
    }
}
