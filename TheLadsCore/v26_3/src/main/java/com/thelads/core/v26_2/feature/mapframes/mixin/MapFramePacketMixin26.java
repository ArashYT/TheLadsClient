package com.thelads.core.v26_2.feature.mapframes.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.perf.MapFrames;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Map frames: a map packet with markers only (no colour patch) does not force a 128x128 texture re-upload. */
@Mixin(ClientPacketListener.class)
abstract class MapFramePacketMixin26 {
    @WrapWithCondition(method = "handleMapItemData", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/MapTextureManager;update(Lnet/minecraft/world/level/saveddata/maps/MapId;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V"))
    private boolean lads$mapUpload(MapTextureManager manager, MapId id, MapItemSavedData data, @Local(argsOnly = true) ClientboundMapItemDataPacket packet) {
        if (!MapFrames.enabled() || packet.colorPatch().isPresent()) return true;
        MapFrames.skippedUpload();
        return false;
    }
}
