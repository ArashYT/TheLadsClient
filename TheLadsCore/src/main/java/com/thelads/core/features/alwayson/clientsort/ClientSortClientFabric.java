/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ClientModInitializer
 *  net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
 *  net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
 *  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
 *  net.fabricmc.fabric.api.resource.v1.ResourceLoader
 *  net.fabricmc.fabric.api.resource.v1.pack.PackActivationType
 *  net.fabricmc.loader.api.FabricLoader
 *  net.fabricmc.loader.api.ModContainer
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 */
package com.thelads.core.features.alwayson.clientsort;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.network.ClientRegistration;
import com.thelads.core.features.alwayson.clientsort.util.KeybindManager;
import com.thelads.core.features.alwayson.clientsort.util.Localization;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class ClientSortClientFabric
implements ClientModInitializer {
    public void onInitializeClient() {
        KeybindManager.KEYBINDS.forEach(KeyMappingHelper::registerKeyMapping);
        ClientTickEvents.END_CLIENT_TICK.register(ClientSortClient::afterClientTick);
        ClientRegistration.PAYLOADS_S2C.forEach(ClientSortClientFabric::registerHandlerS2C);
        FabricLoader.getInstance().getModContainer("theladscore").ifPresent(container -> ResourceLoader.registerBuiltinPack((Identifier)Identifier.fromNamespaceAndPath("clientsort", "clientsort-dark-mode"), (ModContainer)container, (Component)Localization.localized("resourcepack", "dark-mode", new Object[0]), (PackActivationType)PackActivationType.NORMAL));
        ClientSortClient.init();
    }

    private static <T extends CustomPacketPayload> void registerHandlerS2C(ClientRegistration.RegisterablePayloadS2C<T> rp) {
        ClientPlayNetworking.registerGlobalReceiver((CustomPacketPayload.Type)rp.type, (payload, context) -> rp.handler.accept((T)payload, context.client(), context.player()));
    }
}
