package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** AutoReconnect (AutoReconnect189): the disconnect reason its filters read. */
@Mixin(GuiDisconnected.class)
public interface GuiDisconnectedAccessor {
    @Accessor IChatComponent getMessage();
}
