package com.thelads.core.v1_20_1.feature.mapframes.mixin;

import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundMapItemDataPacket.class)
public interface MapPacketAccess {
    @Accessor("colorPatch") MapItemSavedData.MapPatch lads$colorPatch();
}
