package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeReconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every server join (server list, direct connect, quick play, transfers) starts here: AutoReconnect remembers it. */
@Mixin(ConnectScreen.class)
abstract class ReconnectTargetMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"), require = 1)
    private static void lads$rememberServer(Screen parent, Minecraft minecraft, ServerAddress address, ServerData server, boolean quickPlay,
                                            TransferState transfer, CallbackInfo ci) {
        NativeReconnect.connecting(address, server, transfer);
    }
}
