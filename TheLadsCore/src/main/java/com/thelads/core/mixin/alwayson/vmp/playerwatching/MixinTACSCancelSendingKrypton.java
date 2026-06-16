/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ChunkMap
 *  org.spongepowered.asm.mixin.Dynamic
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.vmp.playerwatching;

import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ChunkMap.class})
public class MixinTACSCancelSendingKrypton {
    @Inject(method={"sendChunks(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/server/network/ServerPlayerEntity;)V", "sendSpiralChunkWatchPackets(Lnet/minecraft/server/network/ServerPlayerEntity;)V", "unloadChunks(Lnet/minecraft/server/network/ServerPlayerEntity;III)V", "sendChunkWatchPackets(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/server/network/ServerPlayerEntity;)V"}, at={@At(value="HEAD")}, cancellable=true, require=0)
    @Dynamic(value="Compatibility hack for krypton")
    private void preventExtraSendChunks(CallbackInfo ci) {
        ci.cancel();
    }
}

