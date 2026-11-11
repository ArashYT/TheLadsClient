package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.social.P2PHostState26;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Host option "Guest render distance": guests get chunks (and entities) up to the cap only. The integrated server sets its own view
 * distance to the host's every tick, so PlayerList.setViewDistance could not cap guests; this caps per player and leaves the host be.
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapGuestViewMixin {
    @Inject(method = "getPlayerViewDistance", at = @At("RETURN"), cancellable = true, require = 1)
    private void ladsCapGuestView(ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
        int cap = P2PHostState26.guestViewCap(player);
        if (cap > 0 && cir.getReturnValueI() > cap) cir.setReturnValue(cap);
    }
}
