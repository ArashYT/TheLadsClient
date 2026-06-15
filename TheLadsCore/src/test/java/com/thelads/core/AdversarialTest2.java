package com.thelads.core;

import com.thelads.core.client.ModSyncNetworking;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public class AdversarialTest2 {
    public static void main(String[] args) {
        System.out.println("Running Adversarial Test 2...");
        try {
            ByteBuf byteBuf = Unpooled.buffer();
            byteBuf.writeByte(1); // VarInt length 1
            FriendlyByteBuf buf = new FriendlyByteBuf(byteBuf);
            ModSyncNetworking.ModSyncPayload payload = ModSyncNetworking.ModSyncPayload.read(buf);
            System.out.println("Payload read successfully: " + payload.data());
        } catch (Exception e) {
            System.out.println("Caught exception: " + e.getClass().getName());
            e.printStackTrace(System.out);
        }
    }
}
