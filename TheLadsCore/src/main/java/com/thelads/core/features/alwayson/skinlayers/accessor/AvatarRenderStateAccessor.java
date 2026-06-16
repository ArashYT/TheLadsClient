package com.thelads.core.features.alwayson.skinlayers.accessor;

import net.minecraft.client.player.AbstractClientPlayer;

public interface AvatarRenderStateAccessor {
    AbstractClientPlayer skinlayers$getPlayer();
    void skinlayers$setPlayer(AbstractClientPlayer player);
}
