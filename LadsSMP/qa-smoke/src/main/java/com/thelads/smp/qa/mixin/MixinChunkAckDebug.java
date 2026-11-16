package com.thelads.smp.qa.mixin;

import com.thelads.smp.qa.FrameWatch;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test only: logs every chunk batch the client sees (ladsqa/chunkack.txt). */
@Mixin(ClientPacketListener.class)
public class MixinChunkAckDebug {
    @Inject(method = "handleChunkBatchStart", at = @At("HEAD"))
    private void ladsqa$start(ClientboundChunkBatchStartPacket p, CallbackInfo ci) {
        if (FrameWatch.ON) FrameWatch.ack("CLIENT batchStart");
    }

    @Inject(method = "handleChunkBatchFinished", at = @At("HEAD"))
    private void ladsqa$finished(ClientboundChunkBatchFinishedPacket p, CallbackInfo ci) {
        if (FrameWatch.ON) FrameWatch.ack("CLIENT batchFinished size=" + p.batchSize());
    }
}
