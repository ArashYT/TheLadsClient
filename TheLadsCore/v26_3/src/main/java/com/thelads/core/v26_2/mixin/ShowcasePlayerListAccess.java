package com.thelads.core.v26_2.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Showcase director `npctab`: client-only NPCs get tab-list entries (tab list takes, Chat Heads by name). */
@Mixin(ClientPacketListener.class)
public interface ShowcasePlayerListAccess {
    @Accessor("playerInfoMap") Map<UUID, PlayerInfo> lads$playerInfoMap();
    @Accessor("listedPlayers") Set<PlayerInfo> lads$listedPlayers();
}
