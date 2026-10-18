package com.thelads.smp.features.arise.nb1;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S payload sent when a player presses the Carry On Extend throw key.
 */
public record ThrowCarriedPayload() implements CustomPacketPayload {
    public static final ThrowCarriedPayload INSTANCE = new ThrowCarriedPayload();
    public static final Type<ThrowCarriedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "throw_carried"));

    public static final StreamCodec<FriendlyByteBuf, ThrowCarriedPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
