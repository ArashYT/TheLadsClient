package com.thelads.core.v1_8_9.utility.mixin;

import com.thelads.core.client.EnderChestPeek;
import com.thelads.core.v1_8_9.utility.Utility189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.util.ChatComponentTranslation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ender Chest Peek: 1.8.9 turns a window's title into plain text when it opens, so the ender chest is told by the packet's own title
 * (vanilla's container.enderchest translation). TAIL runs on the client thread only, after the chest screen is up.
 */
@Mixin(NetHandlerPlayClient.class)
abstract class EnderWindowMixin189 {
    @Shadow private Minecraft gameController;

    @Inject(method = "handleOpenWindow", at = @At("TAIL"))
    private void lads$enderChest(S2DPacketOpenWindow packet, CallbackInfo ci) {
        if (packet.getWindowTitle() instanceof ChatComponentTranslation && gameController.thePlayer != null
            && EnderChestPeek.TITLE_KEY.equals(((ChatComponentTranslation) packet.getWindowTitle()).getKey()))
            Utility189.enderOpened(gameController.thePlayer.openContainer);
    }
}
