/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.features.alwayson.entityculling.occlusionculling;

import com.thelads.core.features.alwayson.entityculling.occlusionculling.util.Vec3d;

public interface DataProvider {
    public boolean prepareChunk(int var1, int var2);

    public boolean isOpaqueFullCube(int var1, int var2, int var3);

    default public void cleanup() {
    }

    default public void checkingPosition(Vec3d[] targetPoints, int size, Vec3d viewerPosition) {
    }
}

