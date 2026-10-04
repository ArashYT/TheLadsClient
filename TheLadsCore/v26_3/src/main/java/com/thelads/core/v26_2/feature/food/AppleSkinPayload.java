package com.thelads.core.v26_2.feature.food;

import com.thelads.core.client.AppleSkinSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One of AppleSkin's server payloads (AppleSkinSync), kept as the bytes the server sent; NativeFood decodes them. */
record AppleSkinPayload(Type<AppleSkinPayload> type, byte[] data) implements CustomPacketPayload {
    static Type<AppleSkinPayload> type(String channel) {
        return new Type<>(Identifier.fromNamespaceAndPath(AppleSkinSync.NAMESPACE, channel));
    }

    /** Reads every byte the payload holds, so a longer future layout never fails the connection. */
    static StreamCodec<FriendlyByteBuf, AppleSkinPayload> codec(Type<AppleSkinPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.data()), buf -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new AppleSkinPayload(type, data);
        });
    }
}
