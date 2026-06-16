/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.renderer.state.MapRenderState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.map_atlas_generation;

import net.minecraft.client.renderer.state.MapRenderState;
import com.thelads.core.features.alwayson.immediatelyfast.feature.map_atlas_generation.MapAtlasTexture;
import com.thelads.core.mixin.alwayson.immediatelyfast.interfaces.IMapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={MapRenderState.class})
public abstract class MixinMapRenderState
implements IMapRenderState {
    @Unique
    private int immediatelyFast$atlasX;
    @Unique
    private int immediatelyFast$atlasY;
    @Unique
    private MapAtlasTexture immediatelyFast$atlasTexture;

    @Override
    public int immediatelyFast$getAtlasX() {
        return this.immediatelyFast$atlasX;
    }

    @Override
    public void immediatelyFast$setAtlasX(int x) {
        this.immediatelyFast$atlasX = x;
    }

    @Override
    public int immediatelyFast$getAtlasY() {
        return this.immediatelyFast$atlasY;
    }

    @Override
    public void immediatelyFast$setAtlasY(int y) {
        this.immediatelyFast$atlasY = y;
    }

    @Override
    public MapAtlasTexture immediatelyFast$getAtlasTexture() {
        return this.immediatelyFast$atlasTexture;
    }

    @Override
    public void immediatelyFast$setAtlasTexture(MapAtlasTexture atlasTexture) {
        this.immediatelyFast$atlasTexture = atlasTexture;
    }
}

