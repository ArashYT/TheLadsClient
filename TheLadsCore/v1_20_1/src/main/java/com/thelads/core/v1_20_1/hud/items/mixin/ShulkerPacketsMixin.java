package com.thelads.core.v1_20_1.hud.items.mixin;

import com.thelads.core.v1_20_1.hud.items.ShulkerContents1201;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server truth about an opened box (TAIL: client thread, after vanilla applied the packet; the netty pass throws before it). */
@Mixin(ClientPacketListener.class)
abstract class ShulkerPacketsMixin {
    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void lads$open(ClientboundOpenScreenPacket packet, CallbackInfo callback) { ShulkerContents1201.opened(); }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void lads$content(ClientboundContainerSetContentPacket packet, CallbackInfo callback) {
        ShulkerContents1201.content(packet.getContainerId(), packet.getItems());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void lads$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo callback) {
        ShulkerContents1201.slot(packet.getContainerId(), packet.getSlot(), packet.getItem());
    }

    @Inject(method = "handleBlockEvent", at = @At("TAIL"))
    private void lads$event(ClientboundBlockEventPacket packet, CallbackInfo callback) {
        ShulkerContents1201.otherOpening(packet.getPos(), packet.getB0(), packet.getB1());
    }
}
