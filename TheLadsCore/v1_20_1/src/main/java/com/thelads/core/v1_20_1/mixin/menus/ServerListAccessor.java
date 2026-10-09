package com.thelads.core.v1_20_1.mixin.menus;

import java.util.List;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Both lists of a second ServerList that ServerListSharedMixin reads from disk before merging. */
@Mixin(ServerList.class)
public interface ServerListAccessor {
    @Accessor("serverList") List<ServerData> ladsServers();
    @Accessor("hiddenServerList") List<ServerData> ladsHiddenServers();
}
