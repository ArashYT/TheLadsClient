/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.RegistryFriendlyByteBuf
 *  net.minecraft.network.codec.ByteBufCodecs
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  org.jetbrains.annotations.NotNull
 */
package com.thelads.core.features.alwayson.clientsort.network.payload;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public record CollectPayload(int containerId, int[] slotIds, String id) implements CustomPacketPayload
{
    public static final StreamCodec<@NotNull RegistryFriendlyByteBuf, int @NotNull []> VAR_INT_ARRAY = new StreamCodec<RegistryFriendlyByteBuf, int[]>(){

        public int @NotNull [] decode(@NotNull RegistryFriendlyByteBuf byteBuf) {
            return byteBuf.readVarIntArray();
        }

        public void encode(@NotNull RegistryFriendlyByteBuf byteBuf, int @NotNull [] array) {
            byteBuf.writeVarIntArray(array);
        }
    };
    public static final StreamCodec<@NotNull RegistryFriendlyByteBuf, @NotNull CollectPayload> STREAM_CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.VAR_INT, CollectPayload::containerId, VAR_INT_ARRAY, CollectPayload::slotIds, (StreamCodec)ByteBufCodecs.STRING_UTF8, CollectPayload::id, CollectPayload::new);
    public static final Identifier ID = Identifier.fromNamespaceAndPath("clientsort", "collect_c2s");
    public static final // Could not load outer class - annotation placement on inner may be incorrect
    CustomPacketPayload.Type<@NotNull CollectPayload> TYPE = new CustomPacketPayload.Type(ID);

    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
