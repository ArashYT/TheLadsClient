package com.thelads.smp.features.arise.nb6.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-Server packet sent when minigame reaches conclusion (success or failure).
 * Server validates session authenticity, hook existence, and elapsed ticks.
 */
public record FinishFishingMinigamePayload(
        int hookId,
        boolean won,
        int ticksElapsed
) implements CustomPacketPayload {
    public static final Type<FinishFishingMinigamePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "fishing_minigame_finish"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FinishFishingMinigamePayload> CODEC =
            CustomPacketPayload.codec(FinishFishingMinigamePayload::write, FinishFishingMinigamePayload::new);

    public FinishFishingMinigamePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean(), buf.readInt());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(hookId);
        buf.writeBoolean(won);
        buf.writeInt(ticksElapsed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
