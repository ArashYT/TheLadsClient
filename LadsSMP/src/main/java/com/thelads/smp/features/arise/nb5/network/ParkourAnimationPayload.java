package com.thelads.smp.features.arise.nb5.network;

import com.thelads.smp.features.arise.nb5.ParkourActionType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C payload broadcast to tracking players to synchronize third-person
 * parkour movement animations in multiplayer.
 */
public record ParkourAnimationPayload(int entityId, int actionTypeId, int actionTicks) implements CustomPacketPayload {
    public static final Type<ParkourAnimationPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "parkour_anim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ParkourAnimationPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeVarInt(payload.actionTypeId);
                buf.writeVarInt(payload.actionTicks);
            },
            buf -> new ParkourAnimationPayload(
                    buf.readVarInt(),
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
