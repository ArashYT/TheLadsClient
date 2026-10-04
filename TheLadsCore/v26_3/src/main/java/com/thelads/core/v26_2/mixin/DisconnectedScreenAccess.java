package com.thelads.core.v26_2.mixin;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** AutoReconnect (NativeReconnect) reads the disconnect reason its filters check. */
@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccess {
    @Accessor("details") DisconnectionDetails lads$details();
}
