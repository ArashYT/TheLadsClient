package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GuiConnecting's cancel flag and connection (ConnectionTweaks189.cancelledConnect). */
@Mixin(GuiConnecting.class)
public interface GuiConnectingAccessor {
    @Accessor("cancel")
    boolean lads$isCancelled();

    @Accessor("networkManager")
    NetworkManager lads$getNetworkManager();
}
