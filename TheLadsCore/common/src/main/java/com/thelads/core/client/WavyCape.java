package com.thelads.core.client;

import com.thelads.core.modules.WavyCapesModule;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * One player's wavy cape (WavyCapesModule): the physics and the mesh every version draws instead of the rigid cape box.
 *
 * <p>Written for The Lads from scratch (no code of other cape mods): the cape's centre line is a rope of {@link #segments()} links
 * hanging from the shoulders, stepped once per game tick in the body's frame (verlet: each point keeps its velocity, gravity pulls it
 * down, air drag pulls it towards the air's speed past the player plus a gusting wind that runs down the cape as a wave). Each link
 * then stays one link long and between hanging (vanilla's 6 degree lean, more while sneaking) and 110 degrees, so the cape never cuts
 * into the back or legs. Sideways it sways as a whole, from strafing and turning.
 *
 * <p>The mesh is vanilla's cape box (10 x 16 x 1 pixels, the cape texture laid out as vanilla's) cut into those strips and bent along
 * the rope, in the frame vanilla's cape layer draws in after its 0.125 shift back: x across, y down, z away from the back (blocks).
 * Smooth shares the strips' edges (one bent sheet), Blocky draws each strip as its own little box. No allocation per frame or tick.
 */
public final class WavyCape {
    /** Floats per mesh vertex: x y z (blocks), u v (0..1 of the cape texture), nx ny nz. */
    public static final int STRIDE = 8;
    static final float K = 0.15f, DAMP = 0.9f, BASE_GRAVITY = 0.04f, MIN_DEG = 6, MAX_DEG = 110, SWAY_MAX = 12;
    /** Longer than this without a step (not drawn, teleported) and the cape starts hanging again. */
    static final int MAX_GAP = 20;
    private static final float DEG = (float) Math.PI / 180;
    private static final int N = WavyCapesModule.MAX_SEGMENTS;
    private static final Map<Object, WavyCape> CAPES = new WeakHashMap<>();

    /** Rope points 0..segments in blocks: d down, b back from the shoulders; p* the same points one tick earlier. */
    final float[] d = new float[N + 1], b = new float[N + 1], pd = new float[N + 1], pb = new float[N + 1];
    private int segments, lastTick, age;
    private float sway, swayO, swayV, lastYaw;
    private boolean started;
    /** Build scratch: joints in pixels, joint normals, link tangents and normals; the sway's sine and cosine. */
    private final float[] jd = new float[N + 1], jb = new float[N + 1], jnd = new float[N + 1], jnb = new float[N + 1],
        td = new float[N], tb = new float[N], nd = new float[N], nb = new float[N];
    private float sinS, cosS;
    /** The mesh of the last {@link #build}: {@link #quads} quads of 4 vertices of {@link #STRIDE} floats. */
    public final float[] mesh = new float[6 * N * 4 * STRIDE];
    public int quads;

    /** This entity's cape (render thread only); forgotten with the entity. */
    public static WavyCape of(Object entity) {
        WavyCape cape = CAPES.get(entity);
        if (cape == null) CAPES.put(entity, cape = new WavyCape());
        return cape;
    }

    /**
     * Brings the cape to the entity's tick: one step per game tick since the last call (at most 5), or hanging again after a long gap.
     *
     * @param tick the entity's tick count
     * @param dx dy dz how far the entity moved in its last tick, in blocks (dy up)
     * @param bodyYaw the body's yaw at the start of that tick in degrees (the previous-tick value, which inventory previews leave alone)
     * @param lean extra outward degrees: 25 while sneaking where the cape does not follow the body's tilt (1.8.9, 1.20.1), else 0
     */
    public void update(WavyCapesModule settings, int tick, double dx, double dy, double dz, float bodyYaw, float lean) {
        int count = settings.segmentCount();
        float min = (MIN_DEG + lean) * DEG;
        if (!started || count != segments || tick < lastTick || tick - lastTick > MAX_GAP) {
            hang(count, min);
            started = true;
            lastTick = tick;
            lastYaw = bodyYaw;
            return;
        }
        if (tick == lastTick) return;
        float yaw = bodyYaw * DEG, sin = (float) Math.sin(yaw), cos = (float) Math.cos(yaw);
        float forward = (float) (-dx * sin + dz * cos), side = (float) (dx * cos + dz * sin);
        float turn = bodyYaw - lastYaw;
        turn -= 360 * Math.round(turn / 360);
        lastYaw = bodyYaw;
        float gravity = BASE_GRAVITY * settings.gravity.getIntValue() / 50f, wind = settings.wind.getIntValue() / 100f;
        for (int steps = Math.min(tick - lastTick, 5); steps > 0; steps--) step(forward, (float) dy, side * 150 + turn * 0.5f, gravity, wind, min);
        lastTick = tick;
    }

    private void hang(int count, float min) {
        segments = count;
        float link = 1f / count, cd = (float) Math.cos(min) * link, cb = (float) Math.sin(min) * link;
        for (int i = 0; i <= count; i++) {
            pd[i] = d[i] = i * cd;
            pb[i] = b[i] = i * cb;
        }
        sway = swayO = swayV = 0;
    }

    /** One tick: airB / airD the air's speed past the player (backwards, downwards), push the sideways push in degrees. */
    void step(float airB, float airD, float push, float gravity, float wind, float min) {
        age++;
        for (int i = 1; i <= segments; i++) {
            float vd = d[i] - pd[i], vb = b[i] - pb[i];
            pd[i] = d[i];
            pb[i] = b[i];
            // gusts travel down the cape: the phase falls behind with each point
            float gustB = wind * (0.10f + 0.05f * (float) Math.sin(age * 0.21f - i * 0.55f) + 0.03f * (float) Math.sin(age * 0.53f - i * 1.3f));
            float gustD = wind * 0.03f * (float) Math.sin(age * 0.37f - i * 0.9f);
            d[i] += vd * DAMP + gravity + K * (airD + gustD - vd);
            b[i] += vb * DAMP + K * (airB + gustB - vb);
        }
        float link = 1f / segments, max = MAX_DEG * DEG;
        for (int i = 1; i <= segments; i++) { // each link one link long, between hanging (plus lean) and MAX_DEG
            float angle = (float) Math.atan2(b[i] - b[i - 1], d[i] - d[i - 1]);
            angle = Math.max(min, Math.min(max, angle));
            d[i] = d[i - 1] + (float) Math.cos(angle) * link;
            b[i] = b[i - 1] + (float) Math.sin(angle) * link;
        }
        swayO = sway;
        swayV = swayV * 0.6f + (Math.max(-SWAY_MAX, Math.min(SWAY_MAX, push)) - sway) * 0.25f;
        sway += swayV;
    }

    public int segments() { return segments; }

    /** Angle of link i from hanging straight down, in degrees (tests and QA logs). */
    public float linkDegrees(int i) { return (float) Math.atan2(b[i + 1] - b[i], d[i + 1] - d[i]) / DEG; }

    /** Sideways sway at this partial tick, in degrees. */
    public float sway(float partial) { return swayO + (sway - swayO) * partial; }

    /** Builds the mesh at this partial tick in the module's style; returns the quad count. */
    public int build(WavyCapesModule settings, float partial) {
        int n = segments;
        boolean blocky = settings.blocky();
        for (int j = 0; j <= n; j++) {
            jd[j] = (pd[j] + (d[j] - pd[j]) * partial) * 16;
            jb[j] = (pb[j] + (b[j] - pb[j]) * partial) * 16;
        }
        for (int i = 0; i < n; i++) {
            float dd = jd[i + 1] - jd[i], db = jb[i + 1] - jb[i], length = (float) Math.sqrt(dd * dd + db * db);
            td[i] = length < 1e-6f ? 1 : dd / length;
            tb[i] = length < 1e-6f ? 0 : db / length;
            nd[i] = -tb[i]; // outward: the tangent turned towards the back
            nb[i] = td[i];
        }
        for (int j = 0; j <= n; j++) { // a joint's normal: the mean of its links' normals
            int a = Math.max(0, j - 1), c = Math.min(n - 1, j);
            float sd = nd[a] + nd[c], sb = nb[a] + nb[c], length = (float) Math.sqrt(sd * sd + sb * sb);
            jnd[j] = length < 1e-6f ? nd[c] : sd / length;
            jnb[j] = length < 1e-6f ? nb[c] : sb / length;
        }
        float s = sway(partial) * DEG;
        sinS = (float) Math.sin(s);
        cosS = (float) Math.cos(s);
        quads = 0;
        for (int i = 0; i < n; i++) {
            int t = i, u = i + 1;
            float v0 = 1 + 16f * i / n, v1 = 1 + 16f * (i + 1) / n;
            // outward offsets of the strip's top and bottom edge: shared at the joints (Smooth) or the strip's own (Blocky)
            float otd = blocky ? nd[i] : jnd[t], otb = blocky ? nb[i] : jnb[t], oud = blocky ? nd[i] : jnd[u], oub = blocky ? nb[i] : jnb[u];
            // Vanilla's box faces with their vertex order and texture pixels; the frame's x is the box's -x.
            int at = quads++ * 4 * STRIDE; // outer face (box north)
            at = put(at, -5, t, otd, otb, true, 11, v0, 0, otd, otb);
            at = put(at, 5, t, otd, otb, true, 1, v0, 0, otd, otb);
            at = put(at, 5, u, oud, oub, true, 1, v1, 0, oud, oub);
            put(at, -5, u, oud, oub, true, 11, v1, 0, oud, oub);
            at = quads++ * 4 * STRIDE; // inner face (box south), on the rope
            at = put(at, 5, t, 0, 0, false, 22, v0, 0, -otd, -otb);
            at = put(at, -5, t, 0, 0, false, 12, v0, 0, -otd, -otb);
            at = put(at, -5, u, 0, 0, false, 12, v1, 0, -oud, -oub);
            put(at, 5, u, 0, 0, false, 22, v1, 0, -oud, -oub);
            at = quads++ * 4 * STRIDE; // box west = frame +5
            at = put(at, 5, t, otd, otb, true, 1, v0, 1, 0, 0);
            at = put(at, 5, t, 0, 0, false, 0, v0, 1, 0, 0);
            at = put(at, 5, u, 0, 0, false, 0, v1, 1, 0, 0);
            put(at, 5, u, oud, oub, true, 1, v1, 1, 0, 0);
            at = quads++ * 4 * STRIDE; // box east = frame -5
            at = put(at, -5, t, 0, 0, false, 12, v0, -1, 0, 0);
            at = put(at, -5, t, otd, otb, true, 11, v0, -1, 0, 0);
            at = put(at, -5, u, oud, oub, true, 11, v1, -1, 0, 0);
            put(at, -5, u, 0, 0, false, 12, v1, -1, 0, 0);
            if (blocky || i == 0) { // top edge (box down face), facing up the rope
                at = quads++ * 4 * STRIDE;
                at = put(at, -5, t, 0, 0, false, 11, 0, 0, -td[i], -tb[i]);
                at = put(at, 5, t, 0, 0, false, 1, 0, 0, -td[i], -tb[i]);
                at = put(at, 5, t, otd, otb, true, 1, 1, 0, -td[i], -tb[i]);
                put(at, -5, t, otd, otb, true, 11, 1, 0, -td[i], -tb[i]);
            }
            if (blocky || i == n - 1) { // bottom edge (box up face), facing down the rope
                at = quads++ * 4 * STRIDE;
                at = put(at, -5, u, oud, oub, true, 21, 1, 0, td[i], tb[i]);
                at = put(at, 5, u, oud, oub, true, 11, 1, 0, td[i], tb[i]);
                at = put(at, 5, u, 0, 0, false, 11, 0, 0, td[i], tb[i]);
                put(at, -5, u, 0, 0, false, 21, 0, 0, td[i], tb[i]);
            }
        }
        return quads;
    }

    /**
     * QA only: the pixels (ARGB) of a 64x32 test cape each version's QA gives its player. The outer face has a band of colour every two
     * rows and a white centre line, so the bends show; the inner face is dark, the edges grey.
     */
    public static int qaPixel(int x, int y) {
        if (y >= 1 && y <= 16 && x >= 1 && x <= 10) {
            if (x == 5 || x == 6) return 0xFFFFFFFF;
            int band = (y - 1) / 2;
            return 0xFF000000 | new int[] {0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x00ACC1, 0x1E88E5, 0x5E35B1, 0xD81B60}[band];
        }
        if (y >= 1 && y <= 16 && x >= 12 && x <= 21) return (y - 1) / 4 % 2 == 0 ? 0xFF303030 : 0xFF484848;
        if (y >= 1 && y <= 16 && (x == 0 || x == 11)) return 0xFF808080;
        if (y == 0 && x >= 1 && x <= 20) return 0xFFC0C0C0;
        return 0;
    }

    /**
     * One vertex: frame x in pixels, joint j moved (od, ob) pixels out when outer; u v in cape texture pixels (a 64x32 layout); the
     * normal in the frame (x, down, back). The sway turns it all as vanilla's cape turn does: yaw by -sway, then roll by sway.
     */
    private int put(int at, float x, int j, float od, float ob, boolean outer, float u, float v, float nx, float ny, float nz) {
        float y = jd[j] + (outer ? od : 0), z = jb[j] + (outer ? ob : 0);
        float x1 = x * cosS - z * sinS, z1 = x * sinS + z * cosS;
        float n1 = nx * cosS - nz * sinS, nz1 = nx * sinS + nz * cosS;
        float[] m = mesh;
        m[at] = (x1 * cosS - y * sinS) / 16;
        m[at + 1] = (x1 * sinS + y * cosS) / 16;
        m[at + 2] = z1 / 16;
        m[at + 3] = u / 64;
        m[at + 4] = v / 32;
        m[at + 5] = n1 * cosS - ny * sinS;
        m[at + 6] = n1 * sinS + ny * cosS;
        m[at + 7] = nz1;
        return at + STRIDE;
    }
}
