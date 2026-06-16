/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.ints.Int2IntMap
 *  it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *  it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
 *  net.minecraft.client.resources.MapTextureManager
 *  net.minecraft.client.resources.MapTextureManager$MapInstance
 *  net.minecraft.world.level.saveddata.maps.MapId
 *  net.minecraft.world.level.saveddata.maps.MapItemSavedData
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.thelads.core.mixin.alwayson.immediatelyfast.map_atlas_generation;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Collection;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import com.thelads.core.features.alwayson.immediatelyfast.feature.map_atlas_generation.MapAtlasTexture;
import com.thelads.core.mixin.alwayson.immediatelyfast.interfaces.IMapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={MapTextureManager.class})
public abstract class MixinMapTextureManager
implements IMapTextureManager {
    @Unique
    private final Int2ObjectMap<MapAtlasTexture> immediatelyFast$mapAtlasTextures = new Int2ObjectOpenHashMap();
    @Unique
    private final Int2IntMap immediatelyFast$mapIdToAtlasMapping = new Int2IntOpenHashMap();

    @Inject(method={"resetData"}, at={@At(value="RETURN")})
    private void clearMapAtlas(CallbackInfo ci) {
        for (MapAtlasTexture texture : this.immediatelyFast$mapAtlasTextures.values()) {
            texture.close();
        }
        this.immediatelyFast$mapAtlasTextures.clear();
        this.immediatelyFast$mapIdToAtlasMapping.clear();
    }

    @Inject(method={"getOrCreateMapInstance"}, at={@At(value="HEAD")})
    private void createMapAtlasTexture(MapId mapId, MapItemSavedData data, CallbackInfoReturnable<MapTextureManager.MapInstance> cir) {
        this.immediatelyFast$mapIdToAtlasMapping.computeIfAbsent(mapId.id(), k -> {
            for (MapAtlasTexture atlasTexture : this.immediatelyFast$mapAtlasTextures.values()) {
                int location = atlasTexture.getNextMapLocation();
                if (location == -1) continue;
                return location;
            }
            MapAtlasTexture atlasTexture = new MapAtlasTexture(this.immediatelyFast$mapAtlasTextures.size());
            this.immediatelyFast$mapAtlasTextures.put(atlasTexture.getId(), atlasTexture);
            return atlasTexture.getNextMapLocation();
        });
    }

    @Override
    public MapAtlasTexture immediatelyFast$getMapAtlasTexture(int id) {
        return (MapAtlasTexture)this.immediatelyFast$mapAtlasTextures.get(id);
    }

    @Override
    public int immediatelyFast$getAtlasMapping(int mapId) {
        return this.immediatelyFast$mapIdToAtlasMapping.getOrDefault(mapId, -1);
    }

    @Override
    public Collection<MapAtlasTexture> immediatelyFast$getAllMapAtlasTextures() {
        return this.immediatelyFast$mapAtlasTextures.values();
    }
}

