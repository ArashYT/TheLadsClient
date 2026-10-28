package com.thelads.smp.features.arise.nb3.cosmeticarmor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S cosmetic armor click: the client only says which slot and what it wants; the server moves the real item
 * (main hand <-> cosmetic slot) and syncs the result. Never carries ItemStacks.
 * New channel id so old clients (that sent four client-built stacks) are simply not offered it.
 *
 * @param slot    0 head, 1 chest, 2 legs, 3 feet
 * @param swap    true = swap the held item with the slot (empty hand takes the cosmetic out)
 * @param visible requested visibility of the slot (used when swap is false)
 */
public record UpdateCosmeticArmorPayload(int slot, boolean swap, boolean visible) implements CustomPacketPayload {
    public static final Type<UpdateCosmeticArmorPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("ladssmp", "cosmetic_armor_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateCosmeticArmorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UpdateCosmeticArmorPayload::slot,
            ByteBufCodecs.BOOL, UpdateCosmeticArmorPayload::swap,
            ByteBufCodecs.BOOL, UpdateCosmeticArmorPayload::visible,
            UpdateCosmeticArmorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
