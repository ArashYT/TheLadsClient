package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.KeepUiOpen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSleepMP;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep UI Open: the server's close-window packet leaves an open chat or your own inventory alone (chests still close). */
@Mixin(NetHandlerPlayClient.class)
public abstract class KeepUiOpenCloseMixin {
    @Inject(method = "handleCloseWindow", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/entity/EntityPlayerSP;closeScreenAndDropStack()V"), cancellable = true)
    private void lads$keepOpen(S2EPacketCloseWindow packet, CallbackInfo ci) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        boolean chat = screen instanceof GuiChat && !(screen instanceof GuiSleepMP);
        if (KeepUiOpen.keepOnServerClose(chat ? KeepUiOpen.Kind.CHAT : KeepUiOpen.Kind.OTHER,
            screen instanceof GuiInventory || screen instanceof GuiContainerCreative)) ci.cancel();
    }
}
