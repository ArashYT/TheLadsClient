package com.thelads.smp.features.arise.nb5.network;

import com.thelads.smp.features.arise.nb5.ParkourActionType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S payload sent when a client requests/initiates a parkour movement action.
 * The server verifies stamina, cooldowns, and physical geometry before accepting.
 */
public record ParkourActionPayload(int actionTypeId, float dirX, float dirY, float dirZ, float param) implements CustomPacketPayload {
    public static final Type<ParkourActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "parkour_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ParkourActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.actionTypeId);
                buf.writeFloat(payload.dirX);
                buf.writeFloat(payload.dirY);
                buf.writeFloat(payload.dirZ);
                buf.writeFloat(payload.param);
            },
            buf -> new ParkourActionPayload(
                    buf.readVarInt(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
            )
    );

    public ParkourActionType getActionType() {
        return ParkourActionType.byId(actionTypeId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
