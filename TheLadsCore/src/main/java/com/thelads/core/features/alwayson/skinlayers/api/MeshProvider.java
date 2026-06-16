package com.thelads.core.features.alwayson.skinlayers.api;

import net.minecraft.client.player.AbstractClientPlayer;

public interface MeshProvider {
    PlayerData getPlayerMesh(AbstractClientPlayer player);
}
