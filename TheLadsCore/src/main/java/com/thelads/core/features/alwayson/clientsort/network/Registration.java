/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.RegistryFriendlyByteBuf
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerPlayer
 */
package com.thelads.core.features.alwayson.clientsort.network;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.network.handler.CollectHandler;
import com.thelads.core.features.alwayson.clientsort.network.handler.SortHandler;
import com.thelads.core.features.alwayson.clientsort.network.handler.StackFillHandler;
import com.thelads.core.features.alwayson.clientsort.network.handler.TransferHandler;
import com.thelads.core.features.alwayson.clientsort.network.payload.CollectPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.CollectResultPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.SortPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.SortResultPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.StackFillPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.StackFillResultPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.TransferPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.TransferResultPayload;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class Registration {
    public static final List<RegisterablePayloadC2S<?>> PAYLOADS_C2S = List.of(new RegisterablePayloadC2S<CollectPayload>(CollectPayload.TYPE, CollectPayload.STREAM_CODEC, CollectHandler::handle), new RegisterablePayloadC2S<SortPayload>(SortPayload.TYPE, SortPayload.STREAM_CODEC, SortHandler::handle), new RegisterablePayloadC2S<StackFillPayload>(StackFillPayload.TYPE, StackFillPayload.STREAM_CODEC, StackFillHandler::handle), new RegisterablePayloadC2S<TransferPayload>(TransferPayload.TYPE, TransferPayload.STREAM_CODEC, TransferHandler::handle));
    public static List<RegisterablePayloadS2C<?>> PAYLOADS_S2C = List.of(new RegisterablePayloadS2C<CollectResultPayload>(CollectResultPayload.TYPE, CollectResultPayload.STREAM_CODEC), new RegisterablePayloadS2C<SortResultPayload>(SortResultPayload.TYPE, SortResultPayload.STREAM_CODEC), new RegisterablePayloadS2C<StackFillResultPayload>(StackFillResultPayload.TYPE, StackFillResultPayload.STREAM_CODEC), new RegisterablePayloadS2C<TransferResultPayload>(TransferResultPayload.TYPE, TransferResultPayload.STREAM_CODEC));

    private Registration() {
    }

    public static class RegisterablePayloadC2S<T extends CustomPacketPayload>
    extends RegisterablePayload<T> {
        public final PayloadHandlerC2S<T> handler;

        public RegisterablePayloadC2S(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, PayloadHandlerC2S<T> handler) {
            super(type, streamCodec);
            this.handler = handler;
        }

        @FunctionalInterface
        public static interface PayloadHandlerC2S<T extends CustomPacketPayload> {
            public void accept(T var1, MinecraftServer var2, ServerPlayer var3);
        }
    }

    public static class RegisterablePayloadS2C<T extends CustomPacketPayload>
    extends RegisterablePayload<T> {
        public RegisterablePayloadS2C(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
            super(type, streamCodec);
        }
    }

    public static abstract class RegisterablePayload<T extends CustomPacketPayload> {
        public final CustomPacketPayload.Type<T> type;
        public final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

        public RegisterablePayload(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
            this.type = type;
            this.streamCodec = streamCodec;
        }
    }
}
