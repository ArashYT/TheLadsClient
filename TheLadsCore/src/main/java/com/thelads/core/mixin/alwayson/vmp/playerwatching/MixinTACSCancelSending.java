/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ChunkMap
 *  net.minecraft.server.level.ChunkTrackingView
 *  net.minecraft.server.level.PlayerMap
 *  net.minecraft.server.level.ServerPlayer
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Overwrite
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching;

import java.util.Collections;
import java.util.Set;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={ChunkMap.class}, priority=1005)
public class MixinTACSCancelSending {
    @Redirect(method={"setServerViewDistance"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/level/PlayerMap;getAllPlayers()Ljava/util/Set;"))
    private Set<ServerPlayer> redirectWatchPacketsOnChangingVD(PlayerMap instance) {
        return Collections.emptySet();
    }

    @Redirect(method={"updatePlayerStatus"}, at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ServerPlayer;setChunkTrackingView(Lnet/minecraft/server/level/ChunkTrackingView;)V"))
    private void redirectChunkFilterSet(ServerPlayer instance, ChunkTrackingView chunkFilter) {
    }

    @Overwrite
    private void updateChunkTracking(ServerPlayer player) {
    }

    @Overwrite
    private void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView chunkFilter) {
    }
}

