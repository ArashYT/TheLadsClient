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

public record StackFillResultPayload(int result, String message) implements CustomPacketPayload
{
    public static final StreamCodec<@NotNull RegistryFriendlyByteBuf, @NotNull StackFillResultPayload> STREAM_CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.INT, StackFillResultPayload::result, (StreamCodec)ByteBufCodecs.STRING_UTF8, StackFillResultPayload::message, StackFillResultPayload::new);
    public static final Identifier ID = Identifier.fromNamespaceAndPath("clientsort", "stack_fill_result_s2c");
    public static final // Could not load outer class - annotation placement on inner may be incorrect
    CustomPacketPayload.Type<@NotNull StackFillResultPayload> TYPE = new CustomPacketPayload.Type(ID);

    @NotNull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
