package com.thelads.core.v1_20_1.mixin.menus;

import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.dto.RealmsServer;
import com.thelads.core.v1_20_1.feature.NativeReconnect1201;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Realms joins do not go through ConnectScreen.startConnecting: AutoReconnect remembers the Realm here. */
@Mixin(RealmsMainScreen.class)
abstract class ReconnectRealmsMixin {
    @Inject(method = "play(Lcom/mojang/realmsclient/dto/RealmsServer;Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void lads$rememberRealm(RealmsServer server, Screen parent, CallbackInfo ci) {
        NativeReconnect1201.connectingRealm((RealmsMainScreen) (Object) this, server);
    }
}
