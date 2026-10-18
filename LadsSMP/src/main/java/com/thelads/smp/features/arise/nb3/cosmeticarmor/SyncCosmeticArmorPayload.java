package com.thelads.smp.features.arise.nb3.cosmeticarmor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record SyncCosmeticArmorPayload(
        UUID targetPlayerUuid,
        ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet,
        boolean headVis, boolean chestVis, boolean legsVis, boolean feetVis
) implements CustomPacketPayload {
    public static final Type<SyncCosmeticArmorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "sync_cosmetic_armor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCosmeticArmorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.targetPlayerUuid());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.head());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.chest());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.legs());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.feet());
                buf.writeBoolean(payload.headVis());
                buf.writeBoolean(payload.chestVis());
                buf.writeBoolean(payload.legsVis());
                buf.writeBoolean(payload.feetVis());
            },
            buf -> new SyncCosmeticArmorPayload(
                    buf.readUUID(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
