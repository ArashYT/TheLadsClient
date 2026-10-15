package com.thelads.core.v26_2.mixin;

import com.thelads.core.client.KeepUiOpen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep UI Open: the server's close-container packet leaves an open chat or your own inventory alone (chests still close). */
@Mixin(ClientPacketListener.class)
public abstract class KeepUiOpenCloseMixin {
    @Inject(method = "handleContainerClose", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;clientSideCloseContainer()V"), cancellable = true, require = 1)
    private void lads$keepOpen(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        Screen screen = Minecraft.getInstance().gui.screen();
        boolean chat = screen instanceof ChatScreen && !(screen instanceof InBedChatScreen);
        if (KeepUiOpen.keepOnServerClose(chat ? KeepUiOpen.Kind.CHAT : KeepUiOpen.Kind.OTHER,
            screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) ci.cancel();
    }
}
