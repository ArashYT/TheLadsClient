/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.interfaces;

import com.thelads.core.features.alwayson.immediatelyfast.feature.map_atlas_generation.MapAtlasTexture;

public interface IMapRenderState {
    public int immediatelyFast$getAtlasX();

    public void immediatelyFast$setAtlasX(int var1);

    public int immediatelyFast$getAtlasY();

    public void immediatelyFast$setAtlasY(int var1);

    public MapAtlasTexture immediatelyFast$getAtlasTexture();

    public void immediatelyFast$setAtlasTexture(MapAtlasTexture var1);
}

