package com.thelads.core.client;

import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ModSyncNetworkingTest {
    @Test
    public void testHandshakePayload() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeUtf("test payload");
        ModSyncNetworking.ModSyncPayload payload = ModSyncNetworking.ModSyncPayload.read(buf);
        assertEquals("test payload", payload.data());
    }

    @Test
    public void testHandshakePayloadEmpty() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ModSyncNetworking.ModSyncPayload payload = ModSyncNetworking.ModSyncPayload.read(buf);
        assertEquals("", payload.data());
    }
}
