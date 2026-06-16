/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ModInitializer
 *  net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
 *  net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
 *  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 */
package com.thelads.core.features.alwayson.clientsort;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.command.ModCommands;
import com.thelads.core.features.alwayson.clientsort.network.Registration;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class ClientSortFabric
implements ModInitializer {
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> new ModCommands().register(dispatcher, buildContext));
        Registration.PAYLOADS_C2S.forEach(ClientSortFabric::registerC2S);
        Registration.PAYLOADS_S2C.forEach(ClientSortFabric::registerPayloadS2C);
        ClientSort.init();
    }

    private static <T extends CustomPacketPayload> void registerC2S(Registration.RegisterablePayloadC2S<T> rp) {
        PayloadTypeRegistry.serverboundPlay().register(rp.type, rp.streamCodec);
        ServerPlayNetworking.registerGlobalReceiver((CustomPacketPayload.Type)rp.type, (payload, context) -> rp.handler.accept((T)payload, context.server(), context.player()));
    }

    private static <T extends CustomPacketPayload> void registerPayloadS2C(Registration.RegisterablePayloadS2C<T> rp) {
        PayloadTypeRegistry.clientboundPlay().register(rp.type, rp.streamCodec);
    }
}
