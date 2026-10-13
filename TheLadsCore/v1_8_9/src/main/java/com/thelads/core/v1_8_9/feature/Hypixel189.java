package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hypixel.HypixelMods;
import com.thelads.core.client.hypixel.RealPing;
import com.thelads.core.config.ModuleSupport;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The Hypixel modules and the real ping (common HypixelMods, RealPing) on 1.8.9: server chat through Forge's chat event (Chat
 * Cleanup cancels it; Kill Banner still sees it, it listens to cancelled events), a client tick, and the Mod API ping on the
 * hypixel:ping channel: sent as a C17 custom payload, its answer read as the S3F arrives on the network thread
 * (NetworkManagerMixin), so the arrival time is the reply's. FML passes channels it has no handler for through to that point.
 */
public final class Hypixel189 {
    private Hypixel189() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(HypixelMods.HYPIXEL_MODS);
        MinecraftForge.EVENT_BUS.register(new Hypixel189());
    }

    @SubscribeEvent
    public void chat(ClientChatReceivedEvent event) {
        if (event.type != 2 && event.message != null && HypixelMods.chat(event.message.getUnformattedText(), System.nanoTime())) event.setCanceled(true);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        HypixelMods.tick(LadsGameBridge.get(), mc.theWorld, mc.getNetHandler(), System.nanoTime());
    }

    /** NetworkManagerMixin, on the network thread: a server plugin message (only hypixel:ping is read, without consuming it). */
    public static void payload(S3FPacketCustomPayload packet) {
        if (!RealPing.CHANNEL.equals(packet.getChannelName())) return;
        long arrived = System.nanoTime();
        ByteBuf buf = packet.getBufferData();
        if (buf == null) return;
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        RealPing.receive(RealPing.CHANNEL, data, arrived);
    }

    /** RealPing's Mod API request (the bridge's sendPluginMessage). */
    public static boolean send(String channel, byte[] data) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null || !mc.getNetHandler().getNetworkManager().isChannelOpen()) return false;
        mc.getNetHandler().addToSendQueue(new C17PacketCustomPayload(channel, new PacketBuffer(Unpooled.wrappedBuffer(data))));
        return true;
    }
}
