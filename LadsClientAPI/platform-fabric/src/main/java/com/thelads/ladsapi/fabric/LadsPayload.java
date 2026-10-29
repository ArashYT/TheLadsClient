package com.thelads.ladsapi.fabric;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Custom packet payload for lads:api channel on Fabric 26.3.
 */
public record LadsPayload(byte[] data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LadsPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.parse("lads:api"));
    public static final StreamCodec<FriendlyByteBuf, LadsPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBytes(payload.data());
        },
        buf -> {
            int len = buf.readableBytes();
            if (len > com.thelads.ladsapi.session.SessionManager.MAX_IN) {
                buf.skipBytes(len);
                return new LadsPayload(new byte[0]);
            }
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            return new LadsPayload(bytes);
        }
    );

    @Override
    public Type<LadsPayload> type() {
        return TYPE;
    }
}
