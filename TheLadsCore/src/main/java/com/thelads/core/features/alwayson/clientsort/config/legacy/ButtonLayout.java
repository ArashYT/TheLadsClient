/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.config.legacy;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.config.Vec2i;
import org.jetbrains.annotations.Nullable;

public record ButtonLayout(String className, @Nullable Vec2i offset, @Nullable Boolean sortEnabled, @Nullable Boolean stackFillEnabled, @Nullable Boolean transferEnabled) {
}
