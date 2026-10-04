package com.thelads.core.v26_2.mixin;

import com.mojang.realmsclient.dto.RealmsServer;
import com.thelads.core.v26_2.feature.NativeReconnect;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.realms.RealmsConnect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Realms joins do not go through ConnectScreen: AutoReconnect remembers the Realm here (ReconnectTargetMixin does servers). */
@Mixin(RealmsConnect.class)
abstract class ReconnectRealmsMixin {
    @Inject(method = "connect", at = @At("HEAD"), require = 1)
    private void lads$rememberRealm(RealmsServer server, ServerAddress address, CallbackInfo ci) {
        NativeReconnect.connectingRealm(server);
    }
}
