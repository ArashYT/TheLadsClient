package com.thelads.core.v26_2.mixin.pvp;

import com.thelads.core.v26_2.feature.PredictionFx26;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drops the server's duplicate of a predicted sound / smash effect once, and swaps the fake pearl for the real one. Packets are only read. */
@Mixin(ClientPacketListener.class)
public class PredictPacketsMixin26 {
    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
    private void lads$dupSound(ClientboundSoundPacket p, CallbackInfo ci) {
        if (PredictionFx26.suppressSound(p.getSound().value(), p.getX(), p.getY(), p.getZ())) ci.cancel();
    }

    @Inject(method = "handleLevelEvent", at = @At("HEAD"), cancellable = true)
    private void lads$dupLevelEvent(ClientboundLevelEventPacket p, CallbackInfo ci) {
        if (PredictionFx26.suppressLevelEvent(p.getType(), p.getPos())) ci.cancel();
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void lads$realPearl(ClientboundAddEntityPacket p, CallbackInfo ci) {
        PredictionFx26.onAddEntity(p);
    }
}
