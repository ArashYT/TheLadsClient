package com.thelads.smp.features.arise.nb6.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-Client packet syncing discovered fish species list for Fishing Journal display.
 */
public record SyncFishingJournalPayload(
        List<String> caughtFish
) implements CustomPacketPayload {
    public static final Type<SyncFishingJournalPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "fishing_journal_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFishingJournalPayload> CODEC =
            CustomPacketPayload.codec(SyncFishingJournalPayload::write, SyncFishingJournalPayload::new);

    public SyncFishingJournalPayload(RegistryFriendlyByteBuf buf) {
        this(readList(buf));
    }

    private static List<String> readList(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(caughtFish.size());
        for (String id : caughtFish) {
            buf.writeUtf(id);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
