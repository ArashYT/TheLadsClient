/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.player.LocalPlayer
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.network.handler;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.network.handler.util.ResultHandlerUtil;
import com.thelads.core.features.alwayson.clientsort.network.handler.validate.PayloadResult;
import com.thelads.core.features.alwayson.clientsort.network.payload.SortPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.SortResultPayload;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

public class SortResultHandler {
    @Nullable
    public static Consumer<PayloadResult> onCompletion;

    private SortResultHandler() {
    }

    public static void handle(SortResultPayload payload, Minecraft mc, LocalPlayer player) {
        mc.execute(() -> {
            PayloadResult result = ResultHandlerUtil.interpretResult(payload.result(), payload.message(), SortPayload.ID);
            if (onCompletion != null) {
                try {
                    onCompletion.accept(result);
                }
                catch (Exception e) {
                    ClientSortClient.LOG.error("Failed to run completion callback for payload '{}' with result '{}': {}", SortPayload.ID, result.name(), e);
                }
                onCompletion = null;
            }
        });
    }
}
