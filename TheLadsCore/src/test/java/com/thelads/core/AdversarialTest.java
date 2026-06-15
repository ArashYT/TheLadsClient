package com.thelads.core;

import com.thelads.core.client.ModSyncNetworking;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public class AdversarialTest {
    public static void main(String[] args) {
        System.out.println("Running Adversarial Test...");
        try {
            // Simulate malformed payload: length prefix says 1000, but only 5 bytes available
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarInt(1000); // 1000 length
            buf.writeBytes(new byte[]{1, 2, 3, 4, 5}); // only 5 bytes

            ModSyncNetworking.ModSyncPayload payload = ModSyncNetworking.ModSyncPayload.read(buf);
            System.out.println("Payload read successfully: " + payload.data());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
