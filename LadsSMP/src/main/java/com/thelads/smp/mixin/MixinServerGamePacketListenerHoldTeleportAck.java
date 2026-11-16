package com.thelads.smp.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The client answers every teleport (a /tp, a portal trip) with an "accept teleportation" packet, and the server's handling of it moves the player, which reads
 * the blocks around it with a blocking chunk read. After a teleport into a dimension or spot that is not generated yet that chunk does not exist, so the server
 * thread stood still until it was generated (12-33 s on a slow CPU: the first Nether chunk loads structure templates and runs the data fixers). Until the player's chunk is loaded,
 * keep the packet (the player's chunk ticket is requested by {@link MixinServerPlayerUnloadedChunk}) and handle it on the first tick after the chunk is there, at the latest after 20 s.
 * The server keeps ticking meanwhile and does not accept movement until it has the answer (the same as a slow connection). In the integrated server of a game client only.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerHoldTeleportAck {
    @Shadow public ServerPlayer player;

    @Shadow public abstract void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket packet);

    @Unique private ServerboundAcceptTeleportationPacket ladssmp$held;
    @Unique private int ladssmp$heldTicks;
    @Unique private boolean ladssmp$releasing;

    @Inject(method = "handleAcceptTeleportPacket", at = @At("HEAD"), cancellable = true, require = 0)
    private void ladssmp$holdUntilChunk(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        if (ladssmp$releasing || FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        if (com.thelads.smp.util.LoadedChunks.isLoaded(player.level(), player.blockPosition())) return;
        ladssmp$held = packet;
        ladssmp$heldTicks = 0;
        player.level().getChunkSource().move(player);
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void ladssmp$releaseWhenLoaded(CallbackInfo ci) {
        if (ladssmp$held == null) return;
        if (!com.thelads.smp.util.LoadedChunks.isLoaded(player.level(), player.blockPosition()) && ++ladssmp$heldTicks <= 400) {
            player.level().getChunkSource().move(player);
            return;
        }
        ServerboundAcceptTeleportationPacket packet = ladssmp$held;
        ladssmp$held = null;
        ladssmp$releasing = true;
        try { handleAcceptTeleportPacket(packet); } finally { ladssmp$releasing = false; }
    }
}
