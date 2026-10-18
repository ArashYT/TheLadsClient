package com.thelads.smp.features.arise.nb3.cosmeticarmor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record UpdateCosmeticArmorPayload(
        ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet,
        boolean headVis, boolean chestVis, boolean legsVis, boolean feetVis
) implements CustomPacketPayload {
    public static final Type<UpdateCosmeticArmorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "update_cosmetic_armor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateCosmeticArmorPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.head());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.chest());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.legs());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.feet());
                buf.writeBoolean(payload.headVis());
                buf.writeBoolean(payload.chestVis());
                buf.writeBoolean(payload.legsVis());
                buf.writeBoolean(payload.feetVis());
            },
            buf -> new UpdateCosmeticArmorPayload(
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
