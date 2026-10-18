package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.ladsapi.LadsApi;
import com.thelads.core.client.ladsapi.LadsApiClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S3FPacketCustomPayload;

/**
 * LadsClientAPI transport adapter for Minecraft 1.8.9 (Forge 1.8.9).
 *
 * <p>Anti-ban:
 * <ul>
 *   <li>The client NEVER announces or registers the {@code lads:api} channel in FML or {@code REGISTER}.</li>
 *   <li>The client sends NOTHING until a valid server {@code hello} has been received.</li>
 *   <li>All payloads are checked against {@link LadsApi#MAX_IN} and {@link LadsApi#MAX_OUT}.</li>
 *   <li>Payloads received on the Netty network thread are copied without advancing readerIndex and scheduled on the client thread.</li>
 * </ul>
 */
public final class LadsApi189 {
    private LadsApi189() {}

    /**
     * Installs the 1.8.9 transport sender once during client initialization.
     */
    public static void register() {
        LadsApiClient.install(LadsApi189::send, "1.8.9");
    }

    /**
     * Checks if the given channel name is exactly {@link LadsApi#CHANNEL} ("lads:api").
     */
    public static boolean isLadsApiChannel(String channel) {
        return LadsApi.CHANNEL.equals(channel);
    }

    /**
     * Validates incoming readable byte count against the protocol limits (1 to {@link LadsApi#MAX_IN}).
     */
    public static boolean isPayloadSizeValid(int readable) {
        return readable > 0 && readable <= LadsApi.MAX_IN;
    }

    /**
     * Validates outgoing payload byte length against the protocol limits (1 to {@link LadsApi#MAX_OUT}).
     */
    public static boolean isSendSizeValid(byte[] payload) {
        return payload != null && payload.length > 0 && payload.length <= LadsApi.MAX_OUT;
    }

    /**
     * Called from {@code NetworkManagerMixin} (channelRead0 HEAD) on the Netty network thread.
     * When the channel is exactly "lads:api", copies readable bytes without advancing the reader index
     * and schedules {@link LadsApiClient#receive(byte[])} on the Minecraft client thread.
     */
    public static void receive(S3FPacketCustomPayload packet) {
        if (packet == null || !isLadsApiChannel(packet.getChannelName())) return;
        ByteBuf buf = packet.getBufferData();
        if (buf == null) return;
        int readable = buf.readableBytes();
        if (!isPayloadSizeValid(readable)) return;
        byte[] payload = new byte[readable];
        buf.getBytes(buf.readerIndex(), payload);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.addScheduledTask(() -> LadsApiClient.receive(payload));
        }
    }

    /**
     * Transport implementation passed to {@link LadsApiClient#install(LadsApiClient.Transport, String)}.
     * Sends a C17PacketCustomPayload via the play network handler only when within {@link LadsApi#MAX_OUT}.
     */
    public static void send(byte[] payload) {
        if (!isSendSizeValid(payload)) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        NetHandlerPlayClient handler = mc.getNetHandler();
        if (handler == null || handler.getNetworkManager() == null || !handler.getNetworkManager().isChannelOpen()) return;
        handler.addToSendQueue(new C17PacketCustomPayload(LadsApi.CHANNEL, new PacketBuffer(Unpooled.wrappedBuffer(payload))));
    }
}
