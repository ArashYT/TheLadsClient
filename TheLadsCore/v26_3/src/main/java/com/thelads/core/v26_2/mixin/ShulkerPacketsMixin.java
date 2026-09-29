package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.ShulkerContents;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ShulkerPacketsMixin {
    @Inject(method = "handleOpenScreen", at = @At("TAIL"), require = 1)
    private void lads$open(ClientboundOpenScreenPacket packet, CallbackInfo callback) { ShulkerContents.opened(); }

    @Inject(method = "handleContainerContent", at = @At("TAIL"), require = 1)
    private void lads$content(ClientboundContainerSetContentPacket packet, CallbackInfo callback) {
        ShulkerContents.content(packet.containerId(), packet.items());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"), require = 1)
    private void lads$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo callback) {
        ShulkerContents.slot(packet.getContainerId(), packet.getSlot(), packet.getItem());
    }

    @Inject(method = "handleBlockEvent", at = @At("TAIL"), require = 1)
    private void lads$event(ClientboundBlockEventPacket packet, CallbackInfo callback) {
        ShulkerContents.otherOpening(packet.getPos(), packet.getB0(), packet.getB1());
    }
}
