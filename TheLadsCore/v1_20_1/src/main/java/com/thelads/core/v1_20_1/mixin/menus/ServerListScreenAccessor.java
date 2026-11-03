package com.thelads.core.v1_20_1.mixin.menus;

import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The screen's server list, for its rows' mixin. */
@Mixin(JoinMultiplayerScreen.class)
public interface ServerListScreenAccessor {
    @Accessor("serverSelectionList") ServerSelectionList ladsServerList();
}
