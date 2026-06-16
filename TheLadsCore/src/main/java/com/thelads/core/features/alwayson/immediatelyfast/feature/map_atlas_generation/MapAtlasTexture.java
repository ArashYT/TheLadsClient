/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.texture.AbstractTexture
 *  net.minecraft.client.renderer.texture.DynamicTexture
 */
package com.thelads.core.features.alwayson.immediatelyfast.feature.map_atlas_generation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.thelads.core.features.alwayson.immediatelyfast.ImmediatelyFast;

public class MapAtlasTexture
implements AutoCloseable {
    public static final int ATLAS_SIZE = ImmediatelyFast.config.map_atlas_size;
    public static final int MAP_SIZE = 128;
    public static final int MAPS_PER_ATLAS = ATLAS_SIZE / 128 * (ATLAS_SIZE / 128);
    private final int id;
    private final Identifier textureId;
    private final DynamicTexture texture;
    private int mapCount;

    public MapAtlasTexture(int id) {
        this.id = id;
        this.textureId = Identifier.fromNamespaceAndPath("immediatelyfast", "map_atlas/" + id);
        this.texture = new DynamicTexture("ImmediatelyFast Map Atlas", ATLAS_SIZE, ATLAS_SIZE, true);
        Minecraft.getInstance().getTextureManager().register(this.textureId, (AbstractTexture)this.texture);
    }

    public int getNextMapLocation() {
        if (this.mapCount >= MAPS_PER_ATLAS) {
            return -1;
        }
        byte atlasX = (byte)(this.mapCount % (ATLAS_SIZE / 128));
        byte atlasY = (byte)(this.mapCount / (ATLAS_SIZE / 128));
        ++this.mapCount;
        return this.id << 16 | atlasX << 8 | atlasY;
    }

    public int getId() {
        return this.id;
    }

    public Identifier getTextureId() {
        return this.textureId;
    }

    public DynamicTexture getTexture() {
        return this.texture;
    }

    public int getMapCount() {
        return this.mapCount;
    }

    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(this.textureId);
    }
}

