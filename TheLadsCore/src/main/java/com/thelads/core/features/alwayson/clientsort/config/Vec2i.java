/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.features.alwayson.clientsort.config;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

public record Vec2i(int x, int y) {
    public Vec2i add(Vec2i other) {
        return new Vec2i(this.x + other.x, this.y + other.y);
    }

    public Vec2i subtract(Vec2i other) {
        return new Vec2i(this.x - other.x, this.y - other.y);
    }

    public boolean equals(Vec2i other) {
        return this.x == other.x && this.y == other.y;
    }
}
