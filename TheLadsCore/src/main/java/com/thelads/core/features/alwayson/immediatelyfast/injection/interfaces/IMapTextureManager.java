/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.interfaces;

import java.util.Collection;
import com.thelads.core.features.alwayson.immediatelyfast.feature.map_atlas_generation.MapAtlasTexture;

public interface IMapTextureManager {
    public MapAtlasTexture immediatelyFast$getMapAtlasTexture(int var1);

    public int immediatelyFast$getAtlasMapping(int var1);

    public Collection<MapAtlasTexture> immediatelyFast$getAllMapAtlasTextures();
}

