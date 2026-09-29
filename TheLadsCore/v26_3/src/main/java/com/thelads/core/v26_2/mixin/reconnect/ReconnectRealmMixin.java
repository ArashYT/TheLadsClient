// SPDX-License-Identifier: LGPL-3.0-only
// API hook adapted from AutoReconnect, Copyright 2023 Bstn1802, 2026 TerminalMC.
package com.thelads.core.v26_2.mixin.reconnect;

import com.mojang.realmsclient.dto.RealmsServer;
import com.thelads.core.v26_2.feature.NativeReconnect;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.realms.RealmsConnect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RealmsConnect.class)
public abstract class ReconnectRealmMixin {
    @Inject(method = "connect", at = @At("HEAD"))
    private void lads$captureRealm(RealmsServer server, ServerAddress address, CallbackInfo ci) { NativeReconnect.realm(server); }
}
