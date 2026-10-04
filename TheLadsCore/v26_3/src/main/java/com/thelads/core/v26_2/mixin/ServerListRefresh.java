package com.thelads.core.v26_2.mixin;

import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The server list's own refresh (its Refresh button and F5), for Ctrl+R (ConnectionTweaks). */
@Mixin(JoinMultiplayerScreen.class)
public interface ServerListRefresh {
    @Invoker("refreshServerList") void lads$refresh();
}
