package com.thelads.core.v1_20_1.mixin.social;

import com.thelads.core.v1_20_1.social.SocialBridge1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to ensure the connect screen displays the host's display name instead of the raw loopback address,
 * one-shot and only for the local P2P loopback port.
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Shadow @Mutable private Component status;

    @Inject(method = "connect(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/resolver/ServerAddress;Lnet/minecraft/client/multiplayer/ServerData;)V", at = @At("HEAD"))
    private void ladsShowDisplayName(Minecraft mc, ServerAddress serverAddress, ServerData serverData, CallbackInfo ci) {
        if (serverAddress != null) {
            String name = SocialBridge1201.consumeLocalConnectDisplayName(serverAddress.getHost(), serverAddress.getPort());
            if (name != null && !name.isEmpty()) {
                this.status = Component.literal("Connecting to " + name + "...");
            }
        }
    }
}
