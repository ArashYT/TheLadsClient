package com.thelads.smp.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After a teleport into a dimension or spot that is not generated yet, the player is ticked while its chunk does not exist, and every block read of that tick
 * (Bits and Balance's campfire and ladder checks, vanilla's own) waits on the server thread until the chunk is generated: 14-25 s of frozen world on a slow CPU.
 * While the chunk is missing, only ask for it (ChunkSource.move gives the player's chunk ticket, generation runs on the worker threads) and skip the tick;
 * the next ticks run as usual once the chunk is there. In the integrated server of a game client only, never longer than 20 s in a row.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayerUnloadedChunk {
    @Unique private int ladssmp$skipped;

    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladssmp$waitForChunk(CallbackInfo ci) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (com.thelads.smp.util.LoadedChunks.isLoaded(self.level(), self.blockPosition()) || ladssmp$skipped > 400) { ladssmp$skipped = 0; return; }
        ladssmp$skipped++;
        self.level().getChunkSource().move(self);
        ci.cancel();
    }
}
