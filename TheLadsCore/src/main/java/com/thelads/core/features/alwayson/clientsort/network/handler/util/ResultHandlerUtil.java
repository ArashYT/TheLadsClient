/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package com.thelads.core.features.alwayson.clientsort.network.handler.util;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.ClientSortClient;
import com.thelads.core.features.alwayson.clientsort.config.Config;
import com.thelads.core.features.alwayson.clientsort.network.handler.validate.PayloadResult;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class ResultHandlerUtil {
    private ResultHandlerUtil() {
    }

    public static PayloadResult interpretResult(int code, String message, Identifier payloadId) {
        PayloadResult result = PayloadResult.get(code);
        if (result.isSuccess()) {
            if (ClientSortClient.debug()) {
                ClientSortClient.LOG.info("Received success result for payload '{}'", payloadId);
            }
        } else {
            ClientSortClient.LOG.warn("Received failure result with code {} ({}) for payload '{}': {}", result.code, result.name(), payloadId, message);
            switch (result) {
                case INCONSISTENT_STATE: 
                case UNSUPPORTED_OP: {
                    if (Config.options().useClientFallback) {
                        ClientSortClient.LOG.info("Client fallback is enabled: retrying without server acceleration.", new Object[0]);
                        break;
                    }
                    if (Minecraft.getInstance().getSingleplayerServer() != null) {
                        ClientSortClient.LOG.info("If you still want to perform this operation, you may either edit the server-side policy in the '{}' config file, disable server acceleration, or enable client fallback. Otherwise, you may edit your client-side policy to disable this operation in this inventory type.", "clientsort-server.json");
                        break;
                    }
                    ClientSortClient.LOG.info("If you still want to perform this operation, you may disable server acceleration or enable client fallback. Otherwise, you may edit your client-side policy to disable this operation in this inventory type.", new Object[0]);
                    break;
                }
                case INVALID_DATA: 
                case FAILURE: {
                    ClientSortClient.LOG.warn("Please make a note of the inventory type and report this to the developer.", new Object[0]);
                    break;
                }
                case UNKNOWN: {
                    ClientSortClient.LOG.error("Result code {} is not recognized. Are you using the same mod version as the server?", code);
                }
            }
        }
        return result;
    }
}
