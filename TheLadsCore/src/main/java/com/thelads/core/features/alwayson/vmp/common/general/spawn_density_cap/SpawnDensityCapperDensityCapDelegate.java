/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.AbstractObject2IntMap
 *  it.unimi.dsi.fastutil.objects.AbstractObject2IntMap$BasicEntry
 *  it.unimi.dsi.fastutil.objects.AbstractObjectIterator
 *  it.unimi.dsi.fastutil.objects.AbstractObjectSet
 *  it.unimi.dsi.fastutil.objects.Object2IntMap
 *  it.unimi.dsi.fastutil.objects.Object2IntMap$Entry
 *  it.unimi.dsi.fastutil.objects.ObjectIterator
 *  it.unimi.dsi.fastutil.objects.ObjectSet
 *  net.minecraft.world.entity.MobCategory
 */
package com.thelads.core.features.alwayson.vmp.common.general.spawn_density_cap;

import it.unimi.dsi.fastutil.objects.AbstractObject2IntMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.world.entity.MobCategory;

public class SpawnDensityCapperDensityCapDelegate {
    public static Object2IntMap<MobCategory> delegateSpawnGroupDensities(final int[] spawnGroupDensities) {
        return new AbstractObject2IntMap<MobCategory>(){

            public int size() {
                return spawnGroupDensities.length;
            }

            public ObjectSet<Object2IntMap.Entry<MobCategory>> object2IntEntrySet() {
                return new AbstractObjectSet<Object2IntMap.Entry<MobCategory>>(){

                    public ObjectIterator<Object2IntMap.Entry<MobCategory>> iterator() {
                        return new AbstractObjectIterator<Object2IntMap.Entry<MobCategory>>(){
                            private int index = 0;

                            public boolean hasNext() {
                                return this.index < spawnGroupDensities.length;
                            }

                            public Object2IntMap.Entry<MobCategory> next() {
                                int index = this.index++;
                                return new AbstractObject2IntMap.BasicEntry((Object)MobCategory.values()[index], spawnGroupDensities[index]);
                            }
                        };
                    }

                    public int size() {
                        return spawnGroupDensities.length;
                    }
                };
            }

            public int getInt(Object key) {
                return spawnGroupDensities[((MobCategory)key).ordinal()];
            }
        };
    }
}

