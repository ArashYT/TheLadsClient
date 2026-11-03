package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** QA: the screen's server list. */
@Mixin(GuiMultiplayer.class)
public interface GuiMultiplayerListAccessor {
    @Accessor("serverListSelector") ServerSelectionList ladsList();
}
