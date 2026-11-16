package com.thelads.smp.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ImmersiveMC ray-casts from every player at the end of every server tick; into a chunk that is not generated yet the ray waits for it on the server thread. Skip the player while its chunk is missing (see MixinBitsAndBalanceLaddersLoadedOnly). */
@Pseudo
@Mixin(targets = "com.hammy275.immersivemc.server.ServerSubscriber", remap = false)
public class MixinImmersiveMcUnloadedChunk {
    @Inject(method = "onPlayerTick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void ladssmp$skipUnloaded(Player player, CallbackInfo ci) {
        if (!com.thelads.smp.util.LoadedChunks.isLoaded(player.level(), player.blockPosition())) ci.cancel();
    }
}
