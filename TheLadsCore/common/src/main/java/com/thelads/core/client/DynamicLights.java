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
    /** More sources than this in one tick are left out: a section's sources are one long bitmask. */
    public static final int MAX_SOURCES = 64;
    private static final Lit NONE = new Lit(new float[0], 15); // before WORLD, whose fields start from it
    public static final DynamicLights WORLD = new DynamicLights();
    private static final long PHI = 0x9E3779B97F4A7C15L;

    /** Rebuild the chunk sections in this box (section coordinates, inclusive). */
    public interface Rebuild { void sections(int minX, int minY, int minZ, int maxX, int maxY, int maxZ); }

    /**
     * What the lookups read, built once per tick the sources change and replaced as a whole: the sources (x, y, z, luminance),
     * per source the squared distance up to which it gives at least each light level (16 per source: a source that
     * cannot beat the brightest so far costs no square root), the box all of them reach, and a hash of chunk section -> bitmask of the sources that can reach into it.
     */
    private static final class Lit {
        final float[] sources, within;
        final long[] keys, masks;
        final int shift, slots;
        final float radius;
        final float minX, minY, minZ, maxX, maxY, maxZ;

        Lit(float[] sources, float radius) {
            this.sources = sources;
            this.radius = radius;
            int n = sources.length / 4;
            within = new float[n * 16];
            float bx0 = Float.MAX_VALUE, by0 = Float.MAX_VALUE, bz0 = Float.MAX_VALUE, bx1 = -Float.MAX_VALUE, by1 = -Float.MAX_VALUE, bz1 = -Float.MAX_VALUE;
            long boxes = 0;
            for (int i = 0; i < n; i++) {
                float x = sources[i * 4], y = sources[i * 4 + 1], z = sources[i * 4 + 2], lum = sources[i * 4 + 3];
                // level L needs lum * (1 - d / radius) + 0.5 >= L, d the distance: d <= radius * (1 - (L - 0.5) / lum)
                within[i * 16] = Float.MAX_VALUE;
                for (int level = 1; level < 16; level++) {
                    float d = radius * (1 - (level - 0.5f) / lum);
                    within[i * 16 + level] = level <= lum ? d * d : -1;
                }
                bx0 = Math.min(bx0, x - radius); by0 = Math.min(by0, y - radius); bz0 = Math.min(bz0, z - radius);
                bx1 = Math.max(bx1, x + radius); by1 = Math.max(by1, y + radius); bz1 = Math.max(bz1, z + radius);
                boxes += (long) span(x, radius) * span(y, radius) * span(z, radius);
            }
            minX = bx0; minY = by0; minZ = bz0; maxX = bx1; maxY = by1; maxZ = bz1;
            int bits = Math.max(4, 64 - Long.numberOfLeadingZeros(boxes * 2));
            slots = 1 << bits;
            shift = 64 - bits;
            keys = new long[n < 2 ? 0 : slots]; // one source: the box is its reach, no hash needed
            masks = new long[keys.length];
            for (int i = 0; keys.length != 0 && i < n; i++) {
                float x = sources[i * 4], y = sources[i * 4 + 1], z = sources[i * 4 + 2];
                for (int sx = low(x, radius); sx <= high(x, radius); sx++)
                    for (int sy = low(y, radius); sy <= high(y, radius); sy++)
                        for (int sz = low(z, radius); sz <= high(z, radius); sz++) {
                            long key = key(sx, sy, sz);
                            int s = (int) ((key * PHI) >>> shift);
                            while (masks[s] != 0 && keys[s] != key) s = (s + 1) & (slots - 1);
                            keys[s] = key;
                            masks[s] |= 1L << i;
                        }
            }
        }

        /** The sources whose light can reach into this section (bit i = source i), 0 for none. */
        long mask(int sx, int sy, int sz) {
            if (keys.length == 0) return sources.length == 0 ? 0 : 1;
            long key = key(sx, sy, sz);
            for (int s = (int) ((key * PHI) >>> shift); ; s = (s + 1) & (slots - 1)) {
                long m = masks[s];
                if (m == 0 || keys[s] == key) return m;
            }
        }

        // A block's centre is within the radius only if the block is in (x - radius - 0.5, x + radius - 0.5): one block of slack each side.
        private static int low(float at, float radius) { return (int) Math.floor(at - radius - 1) >> 4; }
        private static int high(float at, float radius) { return (int) Math.floor(at + radius) >> 4; }
        private static int span(float at, float radius) { return high(at, radius) - low(at, radius) + 1; }
        private static long key(int sx, int sy, int sz) { return ((long) (sx & 0x3FFFFF) << 42) | ((long) (sz & 0x3FFFFF) << 20) | (sy & 0xFFFFF); }
    }

    private volatile Lit lit = NONE;
    private float reach = 15; // client thread only: the radius lit was built with

    // Client thread only: this tick's sources, and each source as it was when its sections were last rebuilt.
    private int[] ids = new int[8], shownIds = new int[8];
    private float[] now = new float[32], shown = new float[32];
    private boolean[] seen = new boolean[8];
    private int count, shownCount, ticks;
    private float shownReach = 15;

    /** The dynamic block light (0-15) at this block: the brightest source, measured to the block's centre. No allocation. */
    public int at(int x, int y, int z) {
        Lit l = lit;
        float[] sources = l.sources;
        if (sources.length == 0) return 0;
        float cx = x + 0.5f, cy = y + 0.5f, cz = z + 0.5f;
        if (cx < l.minX || cx > l.maxX || cy < l.minY || cy > l.maxY || cz < l.minZ || cz > l.maxZ) return 0;
        long m = l.mask(x >> 4, y >> 4, z >> 4);
        float[] within = l.within;
        int best = 0;
        while (m != 0) {
            int i = Long.numberOfTrailingZeros(m);
            m &= m - 1;
            int lum = (int) sources[i * 4 + 3];
            if (lum <= best) continue;
            float dx = sources[i * 4] - cx, dy = sources[i * 4 + 1] - cy, dz = sources[i * 4 + 2] - cz, distance = dx * dx + dy * dy + dz * dz;
            if (distance > within[i * 16 + best + 1]) continue; // cannot beat best: no square root
            best = Math.max(best + 1, Math.min(lum, (int) (lum * (1 - (float) Math.sqrt(distance) / l.radius) + 0.5f)));
        }
        return best;
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
            lit = count == 0 ? NONE : new Lit(Arrays.copyOf(now, count * 4), radius);
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

    /** Nothing lit and nothing waiting to go dark: a disabled feature can skip its ticks. */
    public boolean idle() { return count == 0 && shownCount == 0 && lit == NONE; }

    private boolean same() {
        float[] published = lit.sources;
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
