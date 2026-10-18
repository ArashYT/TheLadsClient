package com.thelads.smp.features.arise.nb5.network;

import com.thelads.smp.features.arise.nb5.ParkourActionType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C payload synchronizing authoritative stamina, exhaustion status,
 * and current confirmed parkour action state from server to client.
 */
public record ParkourSyncPayload(int stamina, boolean exhausted, int actionTypeId, int actionTicks) implements CustomPacketPayload {
    public static final Type<ParkourSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "parkour_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ParkourSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.stamina);
                buf.writeBoolean(payload.exhausted);
                buf.writeVarInt(payload.actionTypeId);
                buf.writeVarInt(payload.actionTicks);
            },
            buf -> new ParkourSyncPayload(
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt()
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
