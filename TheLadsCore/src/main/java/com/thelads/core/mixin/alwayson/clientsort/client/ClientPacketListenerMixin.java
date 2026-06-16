/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.multiplayer.ClientPacketListener
 *  net.minecraft.network.protocol.game.ClientboundLoginPacket
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.clientsort.client;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ClientPacketListener.class})
public abstract class ClientPacketListenerMixin {
    @Inject(method={"handleLogin"}, at={@At(value="HEAD")})
    private void beforeLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientSortClient.searchOrderUpdated = false;
        ClientSortClient.operatingClient = false;
        ClientSortClient.clientOpQueue.clear();
    }

    @Inject(method={"handleLogin"}, at={@At(value="RETURN")})
    private void afterLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientSortClient.updateItemTags(Config.options());
        ClientSortClient.updateItemSets(Config.options());
    }
}
