package com.thelads.core.v1_21_1.mixin;

import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PauseScreen.class)
public interface PauseScreenAccessor {
    /** The pause menu's own Disconnect / Save and Quit action (1.21.1 has no Minecraft.disconnectFromWorld). */
    @Invoker("onDisconnect") void ladsDisconnect();
}
