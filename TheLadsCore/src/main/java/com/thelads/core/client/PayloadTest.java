package com.thelads.core.client;

import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import com.thelads.core.client.ModSyncNetworking.ModSyncPayload;

public class PayloadTest {
    public static void main(String[] args) {
        System.out.println("Starting test");
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<40000; i++) {
            sb.append("a");
        }
        String data = sb.toString();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        
        try {
            ModSyncPayload payload = new ModSyncPayload(data);
            payload.write(buf);
            System.out.println("WRITE SUCCESS");
        } catch (Exception e) {
            System.out.println("WRITE FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
