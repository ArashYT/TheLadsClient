package com.thelads.core.v1_20_1.feature.mapframes.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.perf.MapFrames;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Map frames: a map packet with markers only (no colour patch) does not flag the map texture for a full 128x128 re-upload. */
@Mixin(ClientPacketListener.class)
abstract class MapFramePacketMixin {
    @WrapWithCondition(method = "handleMapItemData", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/MapRenderer;update(ILnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V"))
    private boolean lads$mapUpload(MapRenderer renderer, int id, MapItemSavedData data, @Local(argsOnly = true) ClientboundMapItemDataPacket packet) {
        if (!MapFrames.enabled() || ((MapPacketAccess) packet).lads$colorPatch() != null) return true;
        MapFrames.skippedUpload();
        return false;
    }
}
