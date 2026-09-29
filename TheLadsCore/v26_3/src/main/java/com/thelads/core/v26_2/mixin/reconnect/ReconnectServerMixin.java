// SPDX-License-Identifier: LGPL-3.0-only
// API hook adapted from AutoReconnect, Copyright 2023 Bstn1802, 2026 TerminalMC.
package com.thelads.core.v26_2.mixin.reconnect;

import com.thelads.core.v26_2.feature.NativeReconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ReconnectServerMixin {
    @Inject(method = "connect", at = @At("HEAD"))
    private void lads$captureServer(Minecraft client, ServerAddress address, ServerData data, TransferState transfer, CallbackInfo ci) {
        NativeReconnect.server(address, data, transfer);
    }
}
