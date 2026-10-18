package com.thelads.smp.features.arise.nb6.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-Client packet sent when a fish bites, initiating the fishing minigame HUD.
 */
public record StartFishingMinigamePayload(
        int hookId,
        String fishId,
        float targetCenter,
        float targetWidth,
        float difficulty,
        int maxTicks
) implements CustomPacketPayload {
    public static final Type<StartFishingMinigamePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "fishing_minigame_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartFishingMinigamePayload> CODEC =
            CustomPacketPayload.codec(StartFishingMinigamePayload::write, StartFishingMinigamePayload::new);

    public StartFishingMinigamePayload(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(hookId);
        buf.writeUtf(fishId);
        buf.writeFloat(targetCenter);
        buf.writeFloat(targetWidth);
        buf.writeFloat(difficulty);
        buf.writeInt(maxTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
