package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ChunkPriority26;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smart Chunk Priority (ChunkPriority26): the sort key of the chunk batch the integrated server sends next follows the look
 * direction. Only the order inside one batch changes; which chunks are sent and when stays vanilla. A very large backlog
 * (LAN guest on a slow link) is picked by a bounded selection that this does not touch.
 */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin26 {
    @Inject(method = "sendNextChunks", at = @At("HEAD"), require = 1)
    private void lads$begin(ServerPlayer player, CallbackInfo ci) {
        ChunkPriority26.begin(player);
    }

    @Inject(method = "sendNextChunks", at = @At("RETURN"), require = 1)
    private void lads$end(ServerPlayer player, CallbackInfo ci) {
        ChunkPriority26.end();
    }

    @Inject(method = "lambda$collectChunksToSend$0", at = @At("HEAD"), cancellable = true, require = 1)
    private static void lads$key(ChunkPos center, LevelChunk chunk, CallbackInfoReturnable<Integer> cir) {
        if (!ChunkPriority26.active()) return;
        ChunkPos pos = chunk.getPos();
        cir.setReturnValue(ChunkPriority26.key(pos.x() - center.x(), pos.z() - center.z()));
    }
}
