/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.longs.Long2ObjectFunction
 *  it.unimi.dsi.fastutil.longs.Long2ObjectMap$Entry
 *  it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
 *  it.unimi.dsi.fastutil.longs.LongArrayList
 *  it.unimi.dsi.fastutil.longs.LongComparators
 *  it.unimi.dsi.fastutil.longs.LongListIterator
 *  it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
 *  it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap
 *  it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet
 */
package com.thelads.core.features.alwayson.vmp.common.maps;

import com.thelads.core.features.alwayson.vmp.common.util.SimpleObjectPool;
import io.papermc.paper.util.MCUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import java.util.Comparator;
import java.util.Set;

public class AreaMap<T> {
    private static final boolean DEBUG = Boolean.getBoolean("vmp.debugAreaMap");
    private static final Object[] EMPTY = new Object[0];
    private static final RawObjectLinkedOpenIdentityHashSet<?> EMPTY_SET = new RawObjectLinkedOpenIdentityHashSet();
    private final SimpleObjectPool<RawObjectLinkedOpenIdentityHashSet<T>> pooledHashSets = new SimpleObjectPool<>(unused -> new RawObjectLinkedOpenIdentityHashSet<>(), ts -> ts.clear(), ts -> {
        ts.clear();
        ts.trim(256);
    }, 8192);
    private final Long2ObjectFunction<RawObjectLinkedOpenIdentityHashSet<T>> allocHashSet = unused -> this.pooledHashSets.alloc();
    private final Long2ObjectOpenHashMap<RawObjectLinkedOpenIdentityHashSet<T>> map = new Long2ObjectOpenHashMap();
    private final Reference2IntOpenHashMap<T> viewDistances = new Reference2IntOpenHashMap();
    private final Reference2LongOpenHashMap<T> lastCenters = new Reference2LongOpenHashMap();
    private Listener<T> addListener = null;
    private Listener<T> removeListener = null;
    private final boolean sortListenerCalls;

    public AreaMap() {
        this(null, null, false);
    }

    public AreaMap(Listener<T> addListener, Listener<T> removeListener, boolean sortListenerCalls) {
        this.addListener = addListener;
        this.removeListener = removeListener;
        this.sortListenerCalls = sortListenerCalls;
    }

    public Set<T> getObjectsInRange(long coordinateKey) {
        RawObjectLinkedOpenIdentityHashSet<?> set = (RawObjectLinkedOpenIdentityHashSet<?>)((Object)this.map.get(coordinateKey));
        return (Set<T>) (set != null ? set : EMPTY_SET);
    }

    public Object[] getObjectsInRangeArray(long coordinateKey) {
        RawObjectLinkedOpenIdentityHashSet set = (RawObjectLinkedOpenIdentityHashSet)((Object)this.map.get(coordinateKey));
        return set != null ? set.getRawSet() : EMPTY;
    }

    public void add(T object, int x, int z, int rawViewDistance) {
        int viewDistance = rawViewDistance;
        this.viewDistances.put(object, viewDistance);
        this.lastCenters.put(object, MCUtil.getCoordinateKey(x, z));
        Listener<T> addListener = this.addListener;
        if (this.sortListenerCalls && addListener != null) {
            int length = 2 * viewDistance + 1;
            LongArrayList set = new LongArrayList(length * length);
            for (int xx = x - viewDistance; xx <= x + viewDistance; ++xx) {
                for (int zz = z - viewDistance; zz <= z + viewDistance; ++zz) {
                    this.add0(xx, zz, object, false);
                    set.add(MCUtil.getCoordinateKey(xx, zz));
                }
            }
            this.notifyListenersSorted(object, x, z, addListener, set);
        } else {
            for (int xx = x - viewDistance; xx <= x + viewDistance; ++xx) {
                for (int zz = z - viewDistance; zz <= z + viewDistance; ++zz) {
                    this.add0(xx, zz, object, true);
                }
            }
        }
        this.validate(object, x, z, viewDistance);
    }

    public void remove(T object) {
        if (!this.viewDistances.containsKey(object)) {
            return;
        }
        int viewDistance = this.viewDistances.removeInt(object);
        long lastCenter = this.lastCenters.removeLong(object);
        int x = MCUtil.getCoordinateX(lastCenter);
        int z = MCUtil.getCoordinateZ(lastCenter);
        Listener<T> removeListener = this.removeListener;
        if (this.sortListenerCalls && removeListener != null) {
            int length = 2 * viewDistance + 1;
            LongArrayList set = new LongArrayList(length * length);
            for (int xx = x - viewDistance; xx <= x + viewDistance; ++xx) {
                for (int zz = z - viewDistance; zz <= z + viewDistance; ++zz) {
                    this.remove0(xx, zz, object, false);
                    set.add(MCUtil.getCoordinateKey(xx, zz));
                }
            }
            this.notifyListenersSorted(object, x, z, removeListener, set);
        } else {
            for (int xx = x - viewDistance; xx <= x + viewDistance; ++xx) {
                for (int zz = z - viewDistance; zz <= z + viewDistance; ++zz) {
                    this.remove0(xx, zz, object, true);
                }
            }
        }
        this.validate(object, -1, -1, -1);
    }

    public void update(T object, int x, int z, int rawViewDistance) {
        if (!this.viewDistances.containsKey(object)) {
            throw new IllegalArgumentException("Tried to update %s when not in map".formatted(object));
        }
        int viewDistance = rawViewDistance;
        int oldViewDistance = this.viewDistances.replace(object, viewDistance);
        long oldCenter = this.lastCenters.replace(object, MCUtil.getCoordinateKey(x, z));
        int oldX = MCUtil.getCoordinateX(oldCenter);
        int oldZ = MCUtil.getCoordinateZ(oldCenter);
        this.updateAdds(object, oldX, oldZ, oldViewDistance, x, z, viewDistance);
        this.updateRemovals(object, oldX, oldZ, oldViewDistance, x, z, viewDistance);
        this.validate(object, x, z, viewDistance);
    }

    public int uniqueObjects() {
        return this.lastCenters.size();
    }

    private void updateAdds(T object, int oldX, int oldZ, int oldViewDistance, int newX, int newZ, int newViewDistance) {
        int xLower = oldX - oldViewDistance;
        int xHigher = oldX + oldViewDistance;
        int zLower = oldZ - oldViewDistance;
        int zHigher = oldZ + oldViewDistance;
        Listener<T> addListener = this.addListener;
        if (this.sortListenerCalls && addListener != null) {
            int length = 2 * newViewDistance + 1;
            LongArrayList set = new LongArrayList(length * length);
            for (int xx = newX - newViewDistance; xx <= newX + newViewDistance; ++xx) {
                for (int zz = newZ - newViewDistance; zz <= newZ + newViewDistance; ++zz) {
                    if (AreaMap.isInRange(xLower, xHigher, zLower, zHigher, xx, zz)) continue;
                    this.add0(xx, zz, object, false);
                    set.add(MCUtil.getCoordinateKey(xx, zz));
                }
            }
            this.notifyListenersSorted(object, newX, newZ, addListener, set);
        } else {
            for (int xx = newX - newViewDistance; xx <= newX + newViewDistance; ++xx) {
                for (int zz = newZ - newViewDistance; zz <= newZ + newViewDistance; ++zz) {
                    if (AreaMap.isInRange(xLower, xHigher, zLower, zHigher, xx, zz)) continue;
                    this.add0(xx, zz, object, true);
                }
            }
        }
    }

    private void updateRemovals(T object, int oldX, int oldZ, int oldViewDistance, int newX, int newZ, int newViewDistance) {
        int xLower = newX - newViewDistance;
        int xHigher = newX + newViewDistance;
        int zLower = newZ - newViewDistance;
        int zHigher = newZ + newViewDistance;
        Listener<T> removeListener = this.removeListener;
        if (this.sortListenerCalls && removeListener != null) {
            int length = 2 * oldViewDistance + 1;
            LongArrayList set = new LongArrayList(length * length);
            for (int xx = oldX - oldViewDistance; xx <= oldX + oldViewDistance; ++xx) {
                for (int zz = oldZ - oldViewDistance; zz <= oldZ + oldViewDistance; ++zz) {
                    if (AreaMap.isInRange(xLower, xHigher, zLower, zHigher, xx, zz)) continue;
                    this.remove0(xx, zz, object, false);
                    set.add(MCUtil.getCoordinateKey(xx, zz));
                }
            }
            this.notifyListenersSorted(object, newX, newZ, removeListener, set);
        } else {
            for (int xx = oldX - oldViewDistance; xx <= oldX + oldViewDistance; ++xx) {
                for (int zz = oldZ - oldViewDistance; zz <= oldZ + oldViewDistance; ++zz) {
                    if (AreaMap.isInRange(xLower, xHigher, zLower, zHigher, xx, zz)) continue;
                    this.remove0(xx, zz, object, true);
                }
            }
        }
    }

    private void add0(int xx, int zz, T object, boolean notifyListeners) {
        RawObjectLinkedOpenIdentityHashSet set = (RawObjectLinkedOpenIdentityHashSet)((Object)this.map.computeIfAbsent(MCUtil.getCoordinateKey(xx, zz), this.allocHashSet));
        set.add(object);
        if (notifyListeners && this.addListener != null) {
            this.addListener.accept(object, xx, zz);
        }
    }

    private void remove0(int xx, int zz, T object, boolean notifyListeners) {
        long coordinateKey = MCUtil.getCoordinateKey(xx, zz);
        RawObjectLinkedOpenIdentityHashSet set = (RawObjectLinkedOpenIdentityHashSet)((Object)this.map.get(coordinateKey));
        if (set == null) {
            throw new IllegalStateException("Expect non-null set in [%d,%d]".formatted(xx, zz));
        }
        if (!set.remove(object)) {
            throw new IllegalStateException("Expect %s in %s ([%d,%d])".formatted(new Object[]{object, set, xx, zz}));
        }
        if (set.isEmpty()) {
            this.map.remove(coordinateKey);
            this.pooledHashSets.release(set);
        }
        if (notifyListeners && this.removeListener != null) {
            this.removeListener.accept(object, xx, zz);
        }
    }

    private void notifyListenersSorted(T object, int x, int z, Listener<T> addListener, LongArrayList set) {
        set.sort(LongComparators.asLongComparator(Comparator.comparingLong(l -> AreaMap.chebyshevDistance(x, z, MCUtil.getCoordinateX(l), MCUtil.getCoordinateZ(l)))));
        LongListIterator iterator = set.iterator();
        while (iterator.hasNext()) {
            long pos = iterator.nextLong();
            addListener.accept(object, MCUtil.getCoordinateX(pos), MCUtil.getCoordinateZ(pos));
        }
    }

    private static boolean isInRange(int xLower, int xHigher, int zLower, int zHigher, int x, int z) {
        return x >= xLower && x <= xHigher && z >= zLower && z <= zHigher;
    }

    private static int chebyshevDistance(int x0, int z0, int x1, int z1) {
        return Math.max(Math.abs(x0 - x1), Math.abs(z0 - z1));
    }

    private void validate(T object, int x, int z, int viewDistance) {
        if (!DEBUG) {
            return;
        }
        if (viewDistance < 0) {
            for (Long2ObjectMap.Entry entry : this.map.long2ObjectEntrySet()) {
                if (!((RawObjectLinkedOpenIdentityHashSet)((Object)entry.getValue())).contains(object)) continue;
                throw new IllegalStateException("Unexpected %s in %s ([%d,%d])".formatted(object, entry.getValue(), MCUtil.getCoordinateX(entry.getLongKey()), MCUtil.getCoordinateZ(entry.getLongKey())));
            }
        } else {
            for (int xx = x - viewDistance; xx <= x + viewDistance; ++xx) {
                for (int zz = z - viewDistance; zz <= z + viewDistance; ++zz) {
                    long coordinateKey = MCUtil.getCoordinateKey(xx, zz);
                    RawObjectLinkedOpenIdentityHashSet set = (RawObjectLinkedOpenIdentityHashSet)((Object)this.map.get(coordinateKey));
                    if (set == null) {
                        throw new IllegalStateException("Expect non-null set in [%d,%d]".formatted(xx, zz));
                    }
                    if (set.contains(object)) continue;
                    throw new IllegalStateException("Expect %s in %s ([%d,%d])".formatted(new Object[]{object, set, xx, zz}));
                }
            }
        }
    }

    public static interface Listener<T> {
        public void accept(T var1, int var2, int var3);
    }

    private static class RawObjectLinkedOpenIdentityHashSet<E>
    extends ReferenceLinkedOpenHashSet<E> {
        public Object[] getRawSet() {
            return this.key;
        }

        protected void rehash(int newN) {
            if (newN > this.n) {
                super.rehash(newN);
            }
        }
    }
}

