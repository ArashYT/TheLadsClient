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

public record SortResultPayload(int result, String message) implements CustomPacketPayload
{
    public static final StreamCodec<@NotNull RegistryFriendlyByteBuf, @NotNull SortResultPayload> STREAM_CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.INT, SortResultPayload::result, (StreamCodec)ByteBufCodecs.STRING_UTF8, SortResultPayload::message, SortResultPayload::new);
    public static final Identifier ID = Identifier.fromNamespaceAndPath("clientsort", "sort_result_s2c");
    public static final // Could not load outer class - annotation placement on inner may be incorrect
    CustomPacketPayload.Type<@NotNull SortResultPayload> TYPE = new CustomPacketPayload.Type(ID);

    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
