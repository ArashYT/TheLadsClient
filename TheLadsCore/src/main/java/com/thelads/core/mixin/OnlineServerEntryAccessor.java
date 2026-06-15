package com.thelads.core.mixin;

import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessor for the selected server entry's data (class may not be public). */
@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public interface OnlineServerEntryAccessor {
    @Accessor("serverData")
    ServerData ladsGetServerData();
}
