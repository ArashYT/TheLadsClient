package com.thelads.smp.features.arise.nb8;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Networking definitions and packet registration for Vein Mining state synchronization.
 */
public final class VeinMiningNetwork {
    public static final Identifier VEIN_STATE_ID = Identifier.fromNamespaceAndPath("ladssmp", "vein_mining_state");

    public record VeinStatePayload(boolean active, int shapeOrdinal, int hitDirection) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<VeinStatePayload> TYPE = new CustomPacketPayload.Type<>(VEIN_STATE_ID);

        public static final StreamCodec<FriendlyByteBuf, VeinStatePayload> STREAM_CODEC = StreamCodec.ofMember(
                VeinStatePayload::write,
                VeinStatePayload::new
        );

        public VeinStatePayload(FriendlyByteBuf buf) {
            this(buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(this.active);
            buf.writeVarInt(this.shapeOrdinal);
            buf.writeVarInt(this.hitDirection);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(VeinStatePayload.TYPE, VeinStatePayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(VeinStatePayload.TYPE, (payload, context) -> {
            int shape = payload.shapeOrdinal();
            int hitDir = payload.hitDirection();

            if (shape < 0 || shape >= VeinShape.values().length) {
                shape = 0;
            }
            if (hitDir < -1 || hitDir > 5) {
                hitDir = -1;
            }

            final int validShape = shape;
            final int validDirection = hitDir;
            final boolean active = payload.active();

            context.server().execute(() -> {
                VeinMiningManager.updatePlayerState(context.player().getUUID(), active, validShape, validDirection);
            });
        });
    }
}
