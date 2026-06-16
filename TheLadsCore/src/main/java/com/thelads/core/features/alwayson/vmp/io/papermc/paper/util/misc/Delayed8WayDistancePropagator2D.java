/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.HashCommon
 *  it.unimi.dsi.fastutil.bytes.ByteArrayFIFOQueue
 *  it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
 *  it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
 *  it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet
 *  it.unimi.dsi.fastutil.longs.LongListIterator
 */
package io.papermc.paper.util.misc;

import io.papermc.paper.util.MCUtil;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.bytes.ByteArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongListIterator;

public final class Delayed8WayDistancePropagator2D {
    protected final LevelMap levels = new LevelMap(16384, 0.6f);
    protected final Long2ByteOpenHashMap sources = new Long2ByteOpenHashMap(4096, 0.6f);
    protected final LongLinkedOpenHashSet updatedSources = new LongLinkedOpenHashSet();
    protected final LevelChangeCallback changeCallback;
    protected final WorkQueue[] levelIncreaseWorkQueues = new WorkQueue[64];
    protected final WorkQueue[] levelRemoveWorkQueues;
    protected long levelIncreaseWorkQueueBitset;
    protected long levelRemoveWorkQueueBitset;

    public Delayed8WayDistancePropagator2D() {
        this(null);
    }

    public Delayed8WayDistancePropagator2D(LevelChangeCallback changeCallback) {
        int i;
        for (i = 0; i < this.levelIncreaseWorkQueues.length; ++i) {
            this.levelIncreaseWorkQueues[i] = new WorkQueue();
        }
        this.levelRemoveWorkQueues = new WorkQueue[64];
        for (i = 0; i < this.levelRemoveWorkQueues.length; ++i) {
            this.levelRemoveWorkQueues[i] = new WorkQueue();
        }
        this.changeCallback = changeCallback;
    }

    public int getLevel(long pos) {
        return this.levels.get(pos);
    }

    public int getLevel(int x, int z) {
        return this.levels.get(MCUtil.getCoordinateKey(x, z));
    }

    public void setSource(int x, int z, int level) {
        this.setSource(MCUtil.getCoordinateKey(x, z), level);
    }

    public void setSource(long coordinate, int level) {
        if ((level & 0x3F) != level || level == 0) {
            throw new IllegalArgumentException("Level must be in (0, 63], not " + level);
        }
        byte byteLevel = (byte)level;
        byte oldLevel = this.sources.put(coordinate, byteLevel);
        if (oldLevel == byteLevel) {
            return;
        }
        this.updatedSources.add(coordinate);
    }

    public void removeSource(int x, int z) {
        this.removeSource(MCUtil.getCoordinateKey(x, z));
    }

    public void removeSource(long coordinate) {
        if (this.sources.remove(coordinate) != 0) {
            this.updatedSources.add(coordinate);
        }
    }

    protected final void addToIncreaseWorkQueue(long coordinate, byte level) {
        WorkQueue queue = this.levelIncreaseWorkQueues[level];
        queue.queuedCoordinates.enqueue(coordinate);
        queue.queuedLevels.enqueue(level);
        this.levelIncreaseWorkQueueBitset |= 1L << level;
    }

    protected final void addToIncreaseWorkQueue(long coordinate, byte index, byte level) {
        WorkQueue queue = this.levelIncreaseWorkQueues[index];
        queue.queuedCoordinates.enqueue(coordinate);
        queue.queuedLevels.enqueue(level);
        this.levelIncreaseWorkQueueBitset |= 1L << index;
    }

    protected final void addToRemoveWorkQueue(long coordinate, byte level) {
        WorkQueue queue = this.levelRemoveWorkQueues[level];
        queue.queuedCoordinates.enqueue(coordinate);
        queue.queuedLevels.enqueue(level);
        this.levelRemoveWorkQueueBitset |= 1L << level;
    }

    public boolean propagateUpdates() {
        if (this.updatedSources.isEmpty()) {
            return false;
        }
        boolean ret = false;
        LongListIterator iterator = this.updatedSources.iterator();
        while (iterator.hasNext()) {
            byte updatedSource;
            long coordinate = iterator.nextLong();
            byte currentLevel = this.levels.get(coordinate);
            if (currentLevel == (updatedSource = this.sources.get(coordinate))) continue;
            ret = true;
            if (updatedSource > currentLevel) {
                this.addToIncreaseWorkQueue(coordinate, updatedSource);
                continue;
            }
            this.addToRemoveWorkQueue(coordinate, currentLevel);
        }
        this.updatedSources.clear();
        this.propagateIncreases();
        this.propagateDecreases();
        return ret;
    }

    protected void propagateIncreases() {
        int queueIndex = 0x3F ^ Long.numberOfLeadingZeros(this.levelIncreaseWorkQueueBitset);
        while (this.levelIncreaseWorkQueueBitset != 0L) {
            WorkQueue queue = this.levelIncreaseWorkQueues[queueIndex];
            while (!queue.queuedLevels.isEmpty()) {
                byte currentLevel;
                boolean neighbourCheck;
                long coordinate = queue.queuedCoordinates.removeFirstLong();
                byte level = queue.queuedLevels.removeFirstByte();
                boolean bl = neighbourCheck = level < 0;
                if (neighbourCheck) {
                    level = (byte)-level;
                    currentLevel = this.levels.get(coordinate);
                } else {
                    currentLevel = this.levels.putIfGreater(coordinate, level);
                }
                if (!neighbourCheck ? currentLevel >= level : currentLevel != level) continue;
                if (this.changeCallback != null) {
                    this.changeCallback.onLevelUpdate(coordinate, currentLevel, level);
                }
                if (level == 1) continue;
                byte neighbourLevel = (byte)(level - 1);
                int x = (int)coordinate;
                int z = (int)(coordinate >>> 32);
                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dz = -1; dz <= 1; ++dz) {
                        if ((dx | dz) == 0) continue;
                        long neighbourCoordinate = MCUtil.getCoordinateKey(x + dx, z + dz);
                        this.addToIncreaseWorkQueue(neighbourCoordinate, neighbourLevel);
                    }
                }
            }
            this.levelIncreaseWorkQueueBitset ^= 1L << queueIndex;
            queueIndex = 0x3F ^ Long.numberOfLeadingZeros(this.levelIncreaseWorkQueueBitset);
        }
    }

    protected void propagateDecreases() {
        int queueIndex = 0x3F ^ Long.numberOfLeadingZeros(this.levelRemoveWorkQueueBitset);
        while (this.levelRemoveWorkQueueBitset != 0L) {
            WorkQueue queue = this.levelRemoveWorkQueues[queueIndex];
            while (!queue.queuedLevels.isEmpty()) {
                byte source;
                byte level;
                long coordinate = queue.queuedCoordinates.removeFirstLong();
                byte currentLevel = this.levels.removeIfGreaterOrEqual(coordinate, level = queue.queuedLevels.removeFirstByte());
                if (currentLevel == 0) continue;
                if (currentLevel > level) {
                    this.addToIncreaseWorkQueue(coordinate, currentLevel, (byte)-currentLevel);
                    continue;
                }
                if (this.changeCallback != null) {
                    this.changeCallback.onLevelUpdate(coordinate, currentLevel, (byte)0);
                }
                if ((source = this.sources.get(coordinate)) != 0) {
                    this.addToIncreaseWorkQueue(coordinate, source);
                }
                if (level == 0) continue;
                byte neighbourLevel = (byte)(level - 1);
                int x = (int)coordinate;
                int z = (int)(coordinate >>> 32);
                for (int dx = -1; dx <= 1; ++dx) {
                    for (int dz = -1; dz <= 1; ++dz) {
                        if ((dx | dz) == 0) continue;
                        long neighbourCoordinate = MCUtil.getCoordinateKey(x + dx, z + dz);
                        this.addToRemoveWorkQueue(neighbourCoordinate, neighbourLevel);
                    }
                }
            }
            this.levelRemoveWorkQueueBitset ^= 1L << queueIndex;
            queueIndex = 0x3F ^ Long.numberOfLeadingZeros(this.levelRemoveWorkQueueBitset);
        }
        this.propagateIncreases();
    }

    @FunctionalInterface
    public static interface LevelChangeCallback {
        public void onLevelUpdate(long var1, byte var3, byte var4);
    }

    protected static final class LevelMap
    extends Long2ByteOpenHashMap {
        public LevelMap() {
        }

        public LevelMap(int expected, float loadFactor) {
            super(expected, loadFactor);
        }

        private int find(long k) {
            if (k == 0L) {
                return this.containsNullKey ? this.n : -(this.n + 1);
            }
            long[] key = this.key;
            int pos = (int)HashCommon.mix((long)k) & this.mask;
            long curr = key[pos];
            if (curr == 0L) {
                return -(pos + 1);
            }
            if (k == curr) {
                return pos;
            }
            while ((curr = key[pos = pos + 1 & this.mask]) != 0L) {
                if (k != curr) continue;
                return pos;
            }
            return -(pos + 1);
        }

        private void insert(int pos, long k, byte v) {
            if (pos == this.n) {
                this.containsNullKey = true;
            }
            this.key[pos] = k;
            this.value[pos] = v;
            if (this.size++ >= this.maxFill) {
                this.rehash(HashCommon.arraySize((int)(this.size + 1), (float)this.f));
            }
        }

        public byte putIfGreater(long key, byte value) {
            int pos = this.find(key);
            if (pos < 0) {
                if (this.defRetValue < value) {
                    this.insert(-pos - 1, key, value);
                }
                return this.defRetValue;
            }
            byte curr = this.value[pos];
            if (value > curr) {
                this.value[pos] = value;
                return curr;
            }
            return curr;
        }

        private void removeEntry(int pos) {
            --this.size;
            this.shiftKeys(pos);
            if (this.n > this.minN && this.size < this.maxFill / 4 && this.n > 16) {
                this.rehash(this.n / 2);
            }
        }

        private void removeNullEntry() {
            this.containsNullKey = false;
            --this.size;
            if (this.n > this.minN && this.size < this.maxFill / 4 && this.n > 16) {
                this.rehash(this.n / 2);
            }
        }

        public byte removeIfGreaterOrEqual(long key, byte value) {
            if (key == 0L) {
                if (!this.containsNullKey) {
                    return this.defRetValue;
                }
                byte current = this.value[this.n];
                if (value >= current) {
                    this.removeNullEntry();
                    return current;
                }
                return current;
            }
            long[] keys = this.key;
            byte[] values = this.value;
            int pos = (int)HashCommon.mix((long)key) & this.mask;
            long curr = keys[pos];
            if (curr == 0L) {
                return this.defRetValue;
            }
            if (key == curr) {
                byte current = values[pos];
                if (value >= current) {
                    this.removeEntry(pos);
                    return current;
                }
                return current;
            }
            while ((curr = keys[pos = pos + 1 & this.mask]) != 0L) {
                if (key != curr) continue;
                byte current = values[pos];
                if (value >= current) {
                    this.removeEntry(pos);
                    return current;
                }
                return current;
            }
            return this.defRetValue;
        }
    }

    protected static final class WorkQueue {
        public final NoResizeLongArrayFIFODeque queuedCoordinates = new NoResizeLongArrayFIFODeque();
        public final NoResizeByteArrayFIFODeque queuedLevels = new NoResizeByteArrayFIFODeque();

        protected WorkQueue() {
        }
    }

    protected static final class NoResizeLongArrayFIFODeque
    extends LongArrayFIFOQueue {
        protected NoResizeLongArrayFIFODeque() {
        }

        public long removeFirstLong() {
            long t = this.array[this.start];
            if (++this.start == this.length) {
                this.start = 0;
            }
            return t;
        }
    }

    protected static final class NoResizeByteArrayFIFODeque
    extends ByteArrayFIFOQueue {
        protected NoResizeByteArrayFIFODeque() {
        }

        public byte removeFirstByte() {
            byte t = this.array[this.start];
            if (++this.start == this.length) {
                this.start = 0;
            }
            return t;
        }
    }
}

