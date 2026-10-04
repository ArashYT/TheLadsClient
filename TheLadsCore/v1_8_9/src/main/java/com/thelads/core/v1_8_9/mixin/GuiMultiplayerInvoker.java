package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiMultiplayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The server list's own refresh (its Refresh button and F5), for Ctrl+R (ConnectionTweaks189). */
@Mixin(GuiMultiplayer.class)
public interface GuiMultiplayerInvoker {
    @Invoker("refreshServerList") void ladsRefresh();
}
