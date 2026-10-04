package com.thelads.core.client;

import java.util.Arrays;

/**
 * Dynamic Lights: moving light sources (a held torch, a burning mob, dropped glowstone) light the world around them without
 * touching the world's light engine. Once per client tick the version adapter lists the sources it sees ({@link #begin},
 * {@link #add}, {@link #end}); chunk meshing and entity rendering, on any thread, then use the brighter of the world's block
 * light and {@link #at}. The light fades evenly from the source's luminance to nothing at the Light Radius. Only the chunk
 * sections around a source that appeared, went, changed or moved far enough are rebuilt.
 */
public final class DynamicLights {
    public static final DynamicLights WORLD = new DynamicLights();
    /** More sources than this in one tick are left out: every light lookup visits each one. */
    public static final int MAX_SOURCES = 64;

    /** Rebuild the chunk sections in this box (section coordinates, inclusive). */
    public interface Rebuild { void sections(int minX, int minY, int minZ, int maxX, int maxY, int maxZ); }

    private static final float[] NONE = {};
    /** What the lookups read: x, y, z, luminance of each source. Replaced, never changed in place. */
    private volatile float[] lit = NONE;
    private volatile float reach = 15;

    // Client thread only: this tick's sources, and each source as it was when its sections were last rebuilt.
    private int[] ids = new int[8], shownIds = new int[8];
    private float[] now = new float[32], shown = new float[32];
    private boolean[] seen = new boolean[8];
    private int count, shownCount, ticks;
    private float shownReach = 15;

    /** The dynamic block light (0-15) at this block: the brightest source, measured to the block's centre. No allocation. */
    public int at(int x, int y, int z) {
        float[] sources = lit;
        if (sources.length == 0) return 0;
        float radius = reach, best = 0, cx = x + 0.5f, cy = y + 0.5f, cz = z + 0.5f;
        for (int i = 0; i < sources.length; i += 4) {
            float dx = sources[i] - cx, dy = sources[i + 1] - cy, dz = sources[i + 2] - cz, distance = dx * dx + dy * dy + dz * dz;
            if (distance >= radius * radius) continue;
            float light = sources[i + 3] * (1 - (float) Math.sqrt(distance) / radius);
            if (light > best) best = light;
        }
        return Math.min(15, (int) (best + 0.5f));
    }

    /** Starts this tick's list of sources. */
    public void begin() { count = 0; }

    /** A source this tick: an entity id (stable while it lives), where its light comes from and its luminance (0 is ignored). */
    public void add(int id, double x, double y, double z, int luminance) {
        if (luminance <= 0 || count == MAX_SOURCES) return;
        if (count == ids.length) {
            ids = Arrays.copyOf(ids, count * 2);
            now = Arrays.copyOf(now, count * 8);
        }
        ids[count] = id;
        now[count * 4] = (float) x;
        now[count * 4 + 1] = (float) y;
        now[count * 4 + 2] = (float) z;
        now[count++ * 4 + 3] = Math.min(15, luminance);
    }

    /**
     * Publishes this tick's sources, then, every {@code everyTicks} ticks, rebuilds the sections whose light changed: around
     * each source that appeared, went, changed luminance or moved at least {@code minMove} blocks since its last rebuild (its
     * old and its new place). A new radius rebuilds around every source.
     */
    public void end(float radius, float minMove, int everyTicks, Rebuild rebuild) {
        if (radius != reach || !same()) {
            reach = radius;
            lit = count == 0 ? NONE : Arrays.copyOf(now, count * 4);
        }
        if (++ticks < everyTicks) return;
        ticks = 0;
        boolean wider = radius != shownReach;
        Arrays.fill(seen, 0, shownCount, false);
        for (int i = 0; i < count; i++) {
            int j = shownIndex(ids[i]);
            if (j >= 0) {
                seen[j] = true;
                if (!wider && shown[j * 4 + 3] == now[i * 4 + 3] && moved(i, j) < minMove * minMove) continue;
                box(shown, j, shownReach, rebuild);
            } else {
                j = shownCount++;
                if (j == shownIds.length) {
                    shownIds = Arrays.copyOf(shownIds, j * 2);
                    shown = Arrays.copyOf(shown, j * 8);
                    seen = Arrays.copyOf(seen, j * 2);
                }
                shownIds[j] = ids[i];
                seen[j] = true;
            }
            System.arraycopy(now, i * 4, shown, j * 4, 4);
            box(now, i, radius, rebuild);
        }
        int kept = 0;
        for (int j = 0; j < shownCount; j++) {
            if (!seen[j]) { box(shown, j, shownReach, rebuild); continue; } // gone: its old place goes dark
            shownIds[kept] = shownIds[j];
            System.arraycopy(shown, j * 4, shown, kept++ * 4, 4);
        }
        shownCount = kept;
        shownReach = radius;
    }

    /** A new level (or none): its sections are built afresh, so the old sources are dropped without rebuilding anything. */
    public void reset() {
        count = shownCount = ticks = 0;
        lit = NONE;
    }

    private boolean same() {
        float[] published = lit;
        if (published.length != count * 4) return false;
        for (int i = 0; i < published.length; i++) if (published[i] != now[i]) return false;
        return true;
    }

    // ponytail: linear id match, fine for MAX_SOURCES; a map if the cap ever grows to hundreds.
    private int shownIndex(int id) {
        for (int j = 0; j < shownCount; j++) if (shownIds[j] == id) return j;
        return -1;
    }

    private float moved(int i, int j) {
        float dx = now[i * 4] - shown[j * 4], dy = now[i * 4 + 1] - shown[j * 4 + 1], dz = now[i * 4 + 2] - shown[j * 4 + 2];
        return dx * dx + dy * dy + dz * dz;
    }

    /** The sections a source lights, one block wider: smooth lighting reads light one block into the next section. */
    private static void box(float[] at, int i, float radius, Rebuild rebuild) {
        float r = radius + 1, x = at[i * 4], y = at[i * 4 + 1], z = at[i * 4 + 2];
        rebuild.sections(section(x - r), section(y - r), section(z - r), section(x + r), section(y + r), section(z + r));
    }

    private static int section(float block) { return (int) Math.floor(block) >> 4; }
}
