/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.Connection
 *  net.minecraft.network.PacketListener
 *  net.minecraft.network.PacketProcessor
 *  net.minecraft.network.TickablePacketListener
 *  net.minecraft.network.protocol.Packet
 *  net.minecraft.network.protocol.PacketUtils
 *  net.minecraft.network.protocol.common.ServerboundResourcePackPacket
 *  net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener
 *  net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.network.CommonListenerCookie
 *  net.minecraft.server.network.ServerCommonPacketListenerImpl
 *  net.minecraft.server.network.ServerConfigurationPacketListenerImpl
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.thelads.core.mixin.alwayson.vmp.networking.config_enforce_main;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ServerConfigurationPacketListenerImpl.class})
public abstract class MixinServerConfigurationNetworkHandler
extends ServerCommonPacketListenerImpl
implements ServerConfigurationPacketListener,
TickablePacketListener {
    public MixinServerConfigurationNetworkHandler(MinecraftServer server, Connection connection, CommonListenerCookie clientData) {
        super(server, connection, clientData);
    }

    @Inject(method={"handleResourcePackResponse"}, at={@At(value="HEAD")})
    private void onResourcePackStatus(ServerboundResourcePackPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread((Packet)packet, (PacketListener)this, (PacketProcessor)this.server.packetProcessor());
    }

    @Inject(method={"handleAcceptCodeOfConduct"}, at={@At(value="HEAD")})
    private void onAcceptCodeOfConduct(ServerboundAcceptCodeOfConductPacket packet, CallbackInfo ci) {
        PacketUtils.ensureRunningOnSameThread((Packet)packet, (PacketListener)this, (PacketProcessor)this.server.packetProcessor());
    }
}

