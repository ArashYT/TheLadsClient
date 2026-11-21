package com.thelads.core.client.audio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deduplication of identical sounds that start in the same tick near each other (arrow volleys, mass mob spawns, piston farms).
 * Online rule: the first {@code threshold} play normally, the last allowed one is boosted by {@link #boost}, the rest are dropped.
 * Main thread only.
 */
public final class SoundDedup {
    public static final int PLAY = 0, DROP = -1;
    private static final int MAX_CLUSTERS = 32;

    private static final class Cluster {
        final double x, y, z; int count;
        Cluster(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    private int tick = Integer.MIN_VALUE;
    private final Map<String, List<Cluster>> groups = new HashMap<String, List<Cluster>>();

    /**
     * Decision for a new sound. Returns {@link #DROP}, {@link #PLAY}, or a count &gt; 1 meaning play it with
     * {@code boost(count)} (it is the last one of its cluster that is allowed).
     */
    public int onSound(int nowTick, String id, double x, double y, double z, int threshold, double distance) {
        if (nowTick != tick) { tick = nowTick; groups.clear(); }
        List<Cluster> list = groups.get(id);
        if (list == null) { list = new ArrayList<Cluster>(); groups.put(id, list); }
        double dSq = distance * distance;
        Cluster hit = null;
        for (Cluster c : list) {
            double dx = c.x - x, dy = c.y - y, dz = c.z - z;
            if (dx * dx + dy * dy + dz * dz <= dSq) { hit = c; break; }
        }
        if (hit == null) {
            if (list.size() < MAX_CLUSTERS) { Cluster c = new Cluster(x, y, z); c.count = 1; list.add(c); }
            return PLAY;
        }
        hit.count++;
        if (hit.count > threshold) return DROP;
        return hit.count == threshold && threshold > 1 ? threshold : PLAY;
    }

    public void clear() { groups.clear(); tick = Integer.MIN_VALUE; }

    /** Volume multiplier of the merged sound: 1 + log10(count). */
    public static float boost(int count) { return count <= 1 ? 1f : (float) (1.0 + Math.log10(count)); }

    /** Sounds that are never merged: caused by the player, UI, music, looping, and the prediction modules' sounds. */
    public static boolean dedupable(String category, String path, boolean positional, boolean looping) {
        if (!positional || looping || path == null) return false;
        if (category != null && (category.equals("MUSIC") || category.equals("RECORDS") || category.equals("MASTER")
            || category.equals("UI") || category.equals("PLAYERS") || category.equals("VOICE"))) return false;
        if (path.startsWith("ui.") || path.contains("player") || path.contains("mace") || path.contains("pearl")
            || path.contains("teleport") || path.contains("attack") || path.contains("sweep")) return false;
        return !(path.contains("door") || path.contains("gate") || path.contains(".break") || path.contains(".place")
            || (path.startsWith("block.") && path.endsWith(".hit")) || path.contains("wood"));
    }
}
