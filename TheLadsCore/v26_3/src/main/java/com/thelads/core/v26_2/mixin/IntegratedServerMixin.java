package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.social.P2PHostState26;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Redirect(
        method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/LanServerPinger;start()V"),
        require = 1
    )
    private void ladsSuppressLanBroadcast(LanServerPinger pinger) {
        if (!P2PHostState26.isLoopbackScope()) {
            pinger.start();
        }
    }
}
