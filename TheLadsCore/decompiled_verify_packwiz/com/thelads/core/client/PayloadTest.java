/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.Unpooled
 *  net.minecraft.network.FriendlyByteBuf
 */
package com.thelads.core.client;

import com.thelads.core.client.ModSyncNetworking;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

public class PayloadTest {
    public static void main(String[] args) {
        System.out.println("Starting test");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40000; ++i) {
            sb.append("a");
        }
        String data = sb.toString();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ModSyncNetworking.ModSyncPayload payload = new ModSyncNetworking.ModSyncPayload(data);
            payload.write(buf);
            System.out.println("WRITE SUCCESS");
        }
        catch (Exception e) {
            System.out.println("WRITE FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

