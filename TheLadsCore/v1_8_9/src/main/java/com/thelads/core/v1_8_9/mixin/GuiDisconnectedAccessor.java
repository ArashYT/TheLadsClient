package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** AutoReconnect (Reconnect189): the disconnect reason its filters read, and where the screen goes back to. */
@Mixin(GuiDisconnected.class)
public interface GuiDisconnectedAccessor {
    @Accessor IChatComponent getMessage();
    @Accessor GuiScreen getParentScreen();
}
