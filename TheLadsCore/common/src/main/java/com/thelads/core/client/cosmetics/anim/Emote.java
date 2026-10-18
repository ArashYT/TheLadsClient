package com.thelads.core.client.cosmetics.anim;

/**
 * One loopable emote: keyframe tracks per channel, sampled at a time with no allocation. Model space is every version's player model:
 * pixels, y down, front = -z, the player's right = -x. Channels per part: x/y/z rotation (radians; the head's are added to where the
 * player looks, every other part's replace the vanilla pose) and x/y/z offsets of the part's pivot from its rest pivot. ROOT is the
 * whole body's offset (pixels), applied by the renderer so armour, held items and capes move with it.
 * <p>
 * Sampling also parents the head and arms to the body's rotation (their pivots turn with the torso) and moves the hips with it, then,
 * with groundLock, lifts or lowers the whole body so the lowest foot corner stands on the ground (bent "knees" read as a dip).
 */
public final class Emote {
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, PARTS = 6;
    public static final int RX = 0, RY = 1, RZ = 2, X = 3, Y = 4, Z = 5;
    /** Whole-body offset channels ROOT + 0..2 (x, y, z pixels). */
    public static final int ROOT = PARTS * 6, CHANNELS = ROOT + 3;
    /** Rest pivots (pixels), as on every version's PlayerModel / ModelPlayer. */
    private static final float[] REST = {0, 0, 0, 0, 0, 0, -5, 2, 0, 5, 2, 0, -1.9f, 12, 0, 1.9f, 12, 0};
    private static final float GROUND = 24, LEG = 12;

    public enum Ease { SMOOTH, EASE, OUT, IN, LINEAR }

    public final String id, name;
    public final float periodMs;
    /** The emote moves this arm, so first person shows it. */
    public final boolean rightArm, leftArm;
    private final boolean groundLock;
    private final float[][] times, values, tangents;
    private final Ease[][] eases;

    private Emote(Builder b) {
        id = b.id; name = b.name; periodMs = b.periodMs; groundLock = b.groundLock;
        times = new float[CHANNELS][]; values = new float[CHANNELS][]; tangents = new float[CHANNELS][]; eases = new Ease[CHANNELS][];
        for (int c = 0; c < CHANNELS; c++) {
            java.util.TreeMap<Float, Object[]> keys = b.keys[c];
            if (keys == null || keys.isEmpty()) continue;
            int n = keys.size(), i = 0;
            times[c] = new float[n]; values[c] = new float[n]; eases[c] = new Ease[n];
            for (var e : keys.entrySet()) { times[c][i] = e.getKey(); values[c][i] = (Float) e.getValue()[0]; eases[c][i++] = (Ease) e.getValue()[1]; }
            tangents[c] = new float[n];
            for (int k = 0; k < n; k++) {
                int p = (k + n - 1) % n, q = (k + 1) % n;
                float tp = times[c][p] - (p >= k ? periodMs : 0), tq = times[c][q] + (q <= k ? periodMs : 0);
                tangents[c][k] = (values[c][q] - values[c][p]) / (tq - tp);
            }
        }
        rightArm = has(RIGHT_ARM); leftArm = has(LEFT_ARM);
    }

    private boolean has(int part) { return times[part * 6] != null || times[part * 6 + 1] != null || times[part * 6 + 2] != null; }

    public static int ch(int part, int axis) { return part * 6 + axis; }
    public static float rest(int part, int axis) { return REST[part * 3 + axis]; }

    /** The pose at {@code tMs} into the emote (any value: the loop wraps) into out[CHANNELS]. */
    public void sample(double tMs, float[] out) {
        float t = (float) (tMs % periodMs);
        if (t < 0) t += periodMs;
        for (int c = 0; c < CHANNELS; c++) out[c] = track(c, t);
        parentToBody(out);
        if (groundLock) out[ROOT + 1] += GROUND - lowestFoot(out);
    }

    private float track(int c, float t) {
        float[] ts = times[c];
        if (ts == null) return 0;
        int n = ts.length;
        float[] v = values[c];
        if (n == 1) return v[0];
        int i = n - 1;
        for (int k = 0; k < n && ts[k] <= t; k++) i = k;
        int j = (i + 1) % n;
        float t0 = ts[i], t1 = j == 0 ? ts[0] + periodMs : ts[j], tt = t < t0 ? t + periodMs : t;
        float dt = t1 - t0, u = (tt - t0) / dt, p0 = v[i], p1 = v[j];
        switch (eases[c][i]) {
            case LINEAR: return p0 + (p1 - p0) * u;
            case EASE: return p0 + (p1 - p0) * u * u * (3 - 2 * u);
            case OUT: { float r = 1 - u; return p0 + (p1 - p0) * (1 - r * r * r); }
            case IN: return p0 + (p1 - p0) * u * u * u;
            default: {
                float u2 = u * u, u3 = u2 * u;
                return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * dt * tangents[c][i]
                    + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * dt * tangents[c][j];
            }
        }
    }

    /** Head and arms turn with the torso around its pivot (the neck); the hips follow it. MC applies rotations as Rz * Ry * Rx. */
    private static void parentToBody(float[] o) {
        float bx = o[ch(BODY, RX)], by = o[ch(BODY, RY)], bz = o[ch(BODY, RZ)];
        if (bx == 0 && by == 0 && bz == 0) return;
        float px = o[ch(BODY, X)], py = o[ch(BODY, Y)], pz = o[ch(BODY, Z)];
        for (int part = HEAD; part <= LEFT_LEG; part++) {
            if (part == BODY) continue;
            int b = part * 6;
            float rx = rest(part, 0) + o[b + X] - px, ry = rest(part, 1) + o[b + Y] - py, rz = rest(part, 2) + o[b + Z] - pz;
            // turn (rx, ry, rz) by the body's rotation
            float cx = (float) Math.cos(bx), sx = (float) Math.sin(bx), cy = (float) Math.cos(by), sy = (float) Math.sin(by);
            float cz = (float) Math.cos(bz), sz = (float) Math.sin(bz);
            float y1 = ry * cx - rz * sx, z1 = ry * sx + rz * cx;
            float x2 = rx * cy + z1 * sy, z2 = -rx * sy + z1 * cy;
            float x3 = x2 * cz - y1 * sz, y3 = x2 * sz + y1 * cz;
            o[b + X] += x3 - rx; o[b + Y] += y3 - ry; o[b + Z] += z2 - rz;
            if (part != RIGHT_LEG && part != LEFT_LEG) { o[b + RX] += bx; o[b + RY] += by; o[b + RZ] += bz; }
        }
    }

    /** Lowest point (largest y) of either foot's four bottom corners. */
    private static float lowestFoot(float[] o) {
        float lowest = -Float.MAX_VALUE;
        for (int part = RIGHT_LEG; part <= LEFT_LEG; part++) {
            int b = part * 6;
            float cx = (float) Math.cos(o[b + RX]), sx = (float) Math.sin(o[b + RX]), cy = (float) Math.cos(o[b + RY]), sy = (float) Math.sin(o[b + RY]);
            float cz = (float) Math.cos(o[b + RZ]), sz = (float) Math.sin(o[b + RZ]);
            for (int corner = 0; corner < 4; corner++) {
                float fx = (corner & 1) == 0 ? -2 : 2, fz = (corner & 2) == 0 ? -2 : 2;
                float y1 = LEG * cx - fz * sx, z1 = LEG * sx + fz * cx;
                float x2 = fx * cy + z1 * sy;
                float y3 = x2 * sz + y1 * cz;
                lowest = Math.max(lowest, rest(part, 1) + o[b + Y] + y3);
            }
        }
        return lowest;
    }

    /** Tracks authored in beats and degrees. */
    public static final class Builder {
        private final String id, name;
        private final float periodMs, beatMs;
        private boolean groundLock;
        @SuppressWarnings("unchecked")
        private final java.util.TreeMap<Float, Object[]>[] keys = new java.util.TreeMap[CHANNELS];

        public Builder(String id, String name, float bpm, int beats) {
            this.id = id; this.name = name; beatMs = 60_000f / bpm; periodMs = beats * beatMs;
        }

        public Builder groundLock() { groundLock = true; return this; }

        /** Keys on one channel: beat, value, beat, value... (degrees for rotations, pixels for offsets), smooth. */
        public Builder track(int channel, float... beatValue) { return track(channel, Ease.SMOOTH, beatValue); }

        public Builder track(int channel, Ease ease, float... beatValue) {
            for (int i = 0; i + 1 < beatValue.length; i += 2) key(channel, beatValue[i], beatValue[i + 1], ease);
            return this;
        }

        /** One key; {@code ease} shapes the way from this key to the next. */
        public Builder key(int channel, float beat, float value, Ease ease) {
            boolean rotation = channel < ROOT && channel % 6 < 3;
            if (keys[channel] == null) keys[channel] = new java.util.TreeMap<>();
            float t = (beat * beatMs) % periodMs;
            keys[channel].put(t < 0 ? t + periodMs : t, new Object[]{rotation ? (float) Math.toRadians(value) : value, ease});
            return this;
        }

        public Emote build() { return new Emote(this); }
    }
}
