package com.thelads.ladsapi.fabric;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class LadsPayloadCodecTest {

    @Test
    public void testPayloadTypeAndBytes() {
        byte[] sampleData = "{\"t\":\"hello\",\"v\":1,\"server\":\"Test Server\"}".getBytes(StandardCharsets.UTF_8);
        LadsPayload payload = new LadsPayload(sampleData);

        assertNotNull(payload.type());
        assertEquals("lads:api", payload.type().id().toString());
        assertArrayEquals(sampleData, payload.data());
    }

    @Test
    public void testEmptyAndLargePayloads() {
        byte[] empty = new byte[0];
        LadsPayload pEmpty = new LadsPayload(empty);
        assertEquals(0, pEmpty.data().length);

        // 512 KB payload (standard maximum policy size)
        byte[] large = new byte[512 * 1024];
        Arrays.fill(large, (byte) 42);
        LadsPayload pLarge = new LadsPayload(large);
        assertEquals(512 * 1024, pLarge.data().length);
        assertEquals((byte) 42, pLarge.data()[0]);
        assertEquals((byte) 42, pLarge.data()[large.length - 1]);
    }
    @Test
    public void testCodecOversizeInboundReturnsEmptyPayload() {
        io.netty.buffer.ByteBuf nettyBuf = io.netty.buffer.Unpooled.buffer();
        byte[] oversize = new byte[35_000];
        Arrays.fill(oversize, (byte) 1);
        nettyBuf.writeBytes(oversize);
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(nettyBuf);

        LadsPayload decoded = LadsPayload.CODEC.decode(buf);
        assertNotNull(decoded);
        assertEquals(0, decoded.data().length);
        assertEquals(0, buf.readableBytes());
    }
}
