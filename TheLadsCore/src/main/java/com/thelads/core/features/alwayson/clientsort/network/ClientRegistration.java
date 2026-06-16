/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.player.LocalPlayer
 *  net.minecraft.network.RegistryFriendlyByteBuf
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 */
package com.thelads.core.features.alwayson.clientsort.network;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.network.handler.CollectResultHandler;
import com.thelads.core.features.alwayson.clientsort.network.handler.SortResultHandler;
import com.thelads.core.features.alwayson.clientsort.network.handler.StackFillResultHandler;
import com.thelads.core.features.alwayson.clientsort.network.handler.TransferResultHandler;
import com.thelads.core.features.alwayson.clientsort.network.Registration;
import com.thelads.core.features.alwayson.clientsort.network.payload.CollectResultPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.SortResultPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.StackFillResultPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.TransferResultPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class ClientRegistration {
    public static List<RegisterablePayloadS2C<?>> PAYLOADS_S2C = List.of(new RegisterablePayloadS2C<CollectResultPayload>(CollectResultPayload.TYPE, CollectResultPayload.STREAM_CODEC, CollectResultHandler::handle), new RegisterablePayloadS2C<SortResultPayload>(SortResultPayload.TYPE, SortResultPayload.STREAM_CODEC, SortResultHandler::handle), new RegisterablePayloadS2C<StackFillResultPayload>(StackFillResultPayload.TYPE, StackFillResultPayload.STREAM_CODEC, StackFillResultHandler::handle), new RegisterablePayloadS2C<TransferResultPayload>(TransferResultPayload.TYPE, TransferResultPayload.STREAM_CODEC, TransferResultHandler::handle));

    private ClientRegistration() {
    }

    public static class RegisterablePayloadS2C<T extends CustomPacketPayload>
    extends Registration.RegisterablePayload<T> {
        public final PayloadHandlerS2C<T> handler;

        public RegisterablePayloadS2C(CustomPacketPayload.Type<T> type, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec, PayloadHandlerS2C<T> handler) {
            super(type, streamCodec);
            this.handler = handler;
        }

        @FunctionalInterface
        public static interface PayloadHandlerS2C<T extends CustomPacketPayload> {
            public void accept(T var1, Minecraft var2, LocalPlayer var3);
        }
    }
}
