package com.thelads.core.v1_20_1.mixin.pvp;

import com.thelads.core.v1_20_1.feature.PredictionFx1201;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drops the server's duplicate of a predicted attack sound once, and swaps the fake pearl for the real one. Packets are only read. */
@Mixin(ClientPacketListener.class)
public class PredictPacketsMixin1201 {
    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true)
    private void lads$dupSound(ClientboundSoundPacket p, CallbackInfo ci) {
        if (PredictionFx1201.suppressSound(p.getSound().value(), p.getX(), p.getY(), p.getZ())) ci.cancel();
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void lads$realPearl(ClientboundAddEntityPacket p, CallbackInfo ci) {
        PredictionFx1201.onAddEntity(p);
    }
}
