package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.gui.PauseMultiplayer189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A join from the pause menu's server list leaves the open world or server first (PauseMultiplayer189.leaveWorld). */
@Mixin(GuiConnecting.class)
public abstract class GuiConnectingMixin189 {
    @Redirect(method = {"<init>(Lnet/minecraft/client/gui/GuiScreen;Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/multiplayer/ServerData;)V",
        "<init>(Lnet/minecraft/client/gui/GuiScreen;Lnet/minecraft/client/Minecraft;Ljava/lang/String;I)V"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Minecraft;loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;)V"), require = 2)
    private void ladsLeaveWorldFirst(Minecraft mc, WorldClient world) {
        PauseMultiplayer189.leaveWorld(mc);
        mc.loadWorld(world);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "connect", at = @At("HEAD"))
    private void ladsPreResolve(String ip, int port, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.thelads.core.v1_8_9.feature.ServerDnsCache189.preResolve(ip);
    }
}
