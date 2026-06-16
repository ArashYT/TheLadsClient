/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.player.LocalPlayer
 */
package com.thelads.core.features.alwayson.clientsort.network.handler;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.network.handler.util.ResultHandlerUtil;
import com.thelads.core.features.alwayson.clientsort.network.handler.validate.PayloadResult;
import com.thelads.core.features.alwayson.clientsort.network.payload.CollectPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.CollectResultPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class CollectResultHandler {
    public static final Map<String, Consumer<PayloadResult>> onCompletion = new HashMap<String, Consumer<PayloadResult>>();

    private CollectResultHandler() {
    }

    public static void handle(CollectResultPayload payload, Minecraft mc, LocalPlayer player) {
        mc.execute(() -> {
            PayloadResult result = ResultHandlerUtil.interpretResult(payload.result(), payload.message(), CollectPayload.ID);
            Consumer<PayloadResult> callback = onCompletion.get(payload.id());
            onCompletion.remove(payload.id());
            if (callback != null) {
                try {
                    callback.accept(result);
                }
                catch (Exception e) {
                    ClientSortClient.LOG.error("Failed to run completion callback for payload '{}' with result '{}': {}", CollectPayload.ID, result.name(), e);
                }
            }
        });
    }
}
