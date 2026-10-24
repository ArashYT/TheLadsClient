package com.thelads.core.v1_20_1.qol.mixin;

import com.thelads.core.v1_20_1.qol.Qol1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Quick Reconnect: every server join (server list, Direct Connect, quick play) is the one Reconnect joins again. */
@Mixin(ConnectScreen.class)
abstract class ConnectScreenQolMixin1201 {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void lads$rememberServer(Screen parent, Minecraft minecraft, ServerAddress address, ServerData server, boolean quickPlay,
                                            CallbackInfo ci) {
        Qol1201.connecting(address, server);
    }
}
