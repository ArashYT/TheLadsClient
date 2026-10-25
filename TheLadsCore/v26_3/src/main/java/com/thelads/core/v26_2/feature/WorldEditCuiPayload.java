package com.thelads.core.v26_2.feature;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Custom packet payload for worldedit:cui on 26.3.
 */
public record WorldEditCuiPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<WorldEditCuiPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("worldedit", "cui"));

    public static final StreamCodec<FriendlyByteBuf, WorldEditCuiPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            if (payload.data != null && payload.data.length > 0) {
                buf.writeBytes(payload.data);
            }
        },
        buf -> {
            int readable = buf.readableBytes();
            byte[] data = new byte[readable];
            buf.readBytes(data);
            return new WorldEditCuiPayload(data);
        }
    );

    @Override
    public Type<WorldEditCuiPayload> type() {
        return TYPE;
    }
}
