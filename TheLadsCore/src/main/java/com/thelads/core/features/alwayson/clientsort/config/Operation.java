/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 */
package com.thelads.core.features.alwayson.clientsort.config;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.network.payload.SortPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.StackFillPayload;
import com.thelads.core.features.alwayson.clientsort.network.payload.TransferPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public enum Operation {
    SORT(SortPayload.TYPE, "sort"),
    STACK_FILL(StackFillPayload.TYPE, "stackFill"),
    MATCH_TRANSFER(TransferPayload.TYPE, "matchTransfer"),
    TRANSFER(TransferPayload.TYPE, "transfer");

    public final CustomPacketPayload.Type<?> type;
    public final Identifier id;
    public final String translationKey;

    private Operation(CustomPacketPayload.Type<?> type, String translationKey) {
        this.type = type;
        this.id = type.id();
        this.translationKey = translationKey;
    }

    public boolean isDirectional() {
        return !this.equals((Object)SORT);
    }
}
