package com.thelads.core.v1_20_1.mixin.menus;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** AutoReconnect (NativeReconnect1201) reads the disconnect reason its filters check. */
@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccess {
    @Accessor("reason") Component lads$reason();
}
