package com.thelads.core.v26_2.feature;

import com.thelads.core.client.ladsapi.LadsApi;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Custom packet payload for the lads:api channel on 26.3.
 * Transports raw bytes up to {@link LadsApi#MAX_IN} without length prefix or compression.
 */
public record LadsApiPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<LadsApiPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(LadsApi.NAMESPACE, LadsApi.PATH));

    public static final StreamCodec<FriendlyByteBuf, LadsApiPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            if (payload.data != null && payload.data.length > 0) {
                buf.writeBytes(payload.data);
            }
        },
        buf -> {
            int readable = buf.readableBytes();
            if (readable > LadsApi.MAX_IN) {
                throw new IllegalArgumentException("lads:api payload exceeds max size: " + readable + " > " + LadsApi.MAX_IN);
            }
            byte[] data = new byte[readable];
            buf.readBytes(data);
            return new LadsApiPayload(data);
        }
    );

    @Override
    public Type<LadsApiPayload> type() {
        return TYPE;
    }
}
