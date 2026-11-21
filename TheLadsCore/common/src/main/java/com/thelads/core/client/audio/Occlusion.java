package com.thelads.core.client.audio;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Occlusion bookkeeping: which sounds get a wall check this tick (cache window + ray budget, round-robin from the nearest) and the
 * gain each one ends up with. The ray casting itself is the version code's {@link Probe}. Main thread only.
 */
public final class Occlusion<K> {
    public static final float GAIN_PER_BLOCK = 0.75f;
    public static final int MAX_BLOCKS = 4;
    public static final int MAX_RAYS_PER_SOUND = 3;

    public interface Probe<K> {
        /** Solid blocks between listener and the sound (0 = clear); add the rays you cast to rays[0]. */
        int blocks(K key, int[] rays);
    }

    private static final class Entry {
        int lastTick; float gain = 1f; boolean fresh = true;
    }

    private final Map<K, Entry> entries = new IdentityHashMap<K, Entry>();
    private int cursor;

    public static float gainFor(int blocks) {
        return (float) Math.pow(GAIN_PER_BLOCK, Math.min(Math.max(blocks, 0), MAX_BLOCKS));
    }

    public float gain(K key) { Entry e = entries.get(key); return e == null ? 1f : e.gain; }

    /** Runs the due probes (nearest first, resuming where the last tick stopped) and returns the rays used. */
    public int update(int tick, int cacheTicks, int budget, List<K> nearestFirst, Probe<K> probe) {
        int n = nearestFirst.size();
        if (n == 0) return 0;
        int[] rays = new int[1];
        int start = cursor % n;
        int done = 0;
        for (int i = 0; i < n && rays[0] < budget; i++) {
            K key = nearestFirst.get((start + i) % n);
            Entry e = entries.get(key);
            if (e == null) { e = new Entry(); e.lastTick = tick - cacheTicks; entries.put(key, e); }
            if (tick - e.lastTick < cacheTicks) { done = i + 1; continue; }
            float target = gainFor(probe.blocks(key, rays));
            e.gain = e.fresh ? target : e.gain + (target - e.gain) * 0.6f; // first reading is exact, later ones ease in
            e.fresh = false;
            e.lastTick = tick;
            done = i + 1;
        }
        cursor = (start + done) % n;
        return rays[0];
    }

    /** Forgets sounds that are no longer playing. */
    public void prune(Map<?, ?> live) { entries.keySet().removeIf(k -> !live.containsKey(k)); }

    public int size() { return entries.size(); }
    public void clear() { entries.clear(); cursor = 0; }
}
