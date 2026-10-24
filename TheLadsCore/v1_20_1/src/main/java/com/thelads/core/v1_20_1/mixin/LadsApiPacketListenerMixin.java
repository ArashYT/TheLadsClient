package com.thelads.core.v1_20_1.mixin;

import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.v1_20_1.net.LadsApiTransport1201;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into ClientPacketListener for LadsClientAPI custom payload interception and connection lifecycle.
 *
 * Priority 500 ensures this runs before Fabric API's ClientPlayNetworkHandlerMixin (priority 999).
 * When a lads:api packet arrives, the payload is copied and handed to LadsApiClient on the game thread,
 * and the handler is cancelled so Fabric API and vanilla never see or release it.
 */
@Mixin(value = ClientPacketListener.class, priority = 500)
public abstract class LadsApiPacketListenerMixin {

    static {
        LadsApiTransport1201.init();
    }

    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$handleCustomPayload(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        ResourceLocation id = packet.getIdentifier();
        if (id != null && ("worldedit:cui".equals(id.toString()) || "WECUI".equals(id.getPath()))) {
            FriendlyByteBuf buf = packet.getData();
            if (buf != null) {
                byte[] payload = new byte[buf.readableBytes()];
                buf.getBytes(buf.readerIndex(), payload);
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.isSameThread()) {
                    com.thelads.core.client.worldedit.WorldEditCUI.onPacket(id.toString(), payload);
                } else if (mc != null) {
                    mc.execute(() -> com.thelads.core.client.worldedit.WorldEditCUI.onPacket(id.toString(), payload));
                }
            }
            ci.cancel();
            return;
        }
        if (LadsApiTransport1201.isLadsApiChannel(id)) {
            FriendlyByteBuf buf = packet.getData();
            if (buf != null) {
                try {
                    int len = buf.readableBytes();
                    if (LadsApiTransport1201.isValidInboundLength(len)) {
                        byte[] payload = new byte[len];
                        buf.readBytes(payload);
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null && mc.isSameThread()) {
                            LadsApiClient.receive(payload);
                        } else if (mc != null) {
                            mc.execute(() -> LadsApiClient.receive(payload));
                        }
                    }
                } finally {
                    if (buf.refCnt() > 0) {
                        buf.release();
                    }
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "handleLogin", at = @At("TAIL"), require = 1)
    private void lads$handleLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        LadsApiClient.joinGame(packet.seed());
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"), require = 1)
    private void lads$handleRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        LadsApiClient.respawn(packet.getSeed());
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"), require = 1)
    private void lads$onDisconnect(Component reason, CallbackInfo ci) {
        LadsApiClient.reset();
    }

    // Leaving through the pause menu (level.disconnect + Minecraft.clearLevel) never reaches onDisconnect on 1.20.1: the closed
    // connection is no longer ticked. clearLevel always calls close(), so the server's rules go here (1.9.19 QA, CHK-06).
    @Inject(method = "close", at = @At("HEAD"), require = 1)
    private void lads$onClose(CallbackInfo ci) {
        LadsApiClient.reset();
    }
}
