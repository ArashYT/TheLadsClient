package com.thelads.core.mixin;

import com.thelads.core.client.AutoReconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void ladsOnStartConnecting(Screen parent, Minecraft mc, ServerAddress addr, ServerData data, boolean b, net.minecraft.client.multiplayer.TransferState transferState, CallbackInfo ci) {
        if (data != null) {
            AutoReconnect.get().setLastServer(data);
        }
    }
}
