package com.thelads.core.v1_20_1.game.skin;

import java.util.Arrays;

/**
 * Geometry of the 3D outer skin layers, as a pure function of the skin's pixels (no Minecraft types, so {@link #main} checks it
 * headless). Every visible texel of an outer-layer box becomes a cuboid standing on the matching base box face and reaching
 * outward; faces between two opaque texels of the same face are left out. Corners are filled by letting border cuboids grow
 * sideways over the edge: the top and bottom faces own all their edges, each side face owns the vertical edge toward the next
 * side clockwise, so exactly one cuboid covers any corner (no two coplanar faces, no z-fighting).
 *
 * <p>Output per part: quads of {@link #FLOATS} floats = 4 corners (x y z, part space, blocks), the texel-centre u v, and the
 * outward normal. Corners are counter-clockwise seen from outside, like vanilla's model cubes.
 */
final class VoxelMesh {
    static final int HAT = 0, JACKET = 1, RIGHT_SLEEVE = 2, LEFT_SLEEVE = 3, RIGHT_PANTS = 4, LEFT_PANTS = 5, PARTS = 6;
    static final int FLOATS = 17;
    /** How far the cuboids reach out of the base box, in skin pixels (vanilla's flat layers float at 0.5 and 0.25). */
    static final float HEAD_DEPTH = 1.0f, BODY_DEPTH = 0.75f;
    /** Each part is shifted by its index times this (pixels), so overlapping parts (legs at rest, shoulders) never share a plane. */
    private static final float NUDGE = 0.012f;
    /** Vanilla's entity shaders discard alpha below 0.1: such texels show nothing flat, so they get no cuboid either. */
    private static final int MIN_ALPHA = 26;

    /** Outer-layer boxes of a player skin: texture u v, size w h d, min corner x y z (model pixels, part space), as PlayerModel. */
    private static final float[][] WIDE = {
        {32, 0, 8, 8, 8, -4, -8, -4},   // hat (head)
        {16, 32, 8, 12, 4, -4, 0, -2},  // jacket (body)
        {40, 32, 4, 12, 4, -3, -2, -2}, // right sleeve
        {48, 48, 4, 12, 4, -1, -2, -2}, // left sleeve
        {0, 32, 4, 12, 4, -2, 0, -2},   // right pants
        {0, 48, 4, 12, 4, -2, 0, -2},   // left pants
    };
    private static final float[][] SLIM = {WIDE[0], WIDE[1], {40, 32, 3, 12, 4, -2, -2, -2}, {48, 48, 3, 12, 4, -1, -2, -2}, WIDE[4], WIDE[5]};

    /*
     * The six faces of a vanilla model cube (ModelPart.Cube's UV layout): texture origin as offsets in units of (d, w), size in
     * (cols, rows) as box dims, the 3D origin corner (0 = min, 1 = max per axis), and the directions of texture +u, texture +v and
     * the outward normal as signed axes (1 = x, 2 = y, 3 = z). Model space has y pointing down, so "DOWN" is the visual top.
     */
    private static final int W = 0, H = 1, D = 2;
    private static final int[][] FACES = {
        // texU = {d, w} multiples, texV = {d}, cols, rows, origin xyz, u-dir, v-dir, normal
        {1, 0, 0, W, D, 0, 0, 1, 1, -3, -2}, // DOWN  (top of the part)
        {1, 1, 0, W, D, 0, 1, 1, 1, -3, 2},  // UP    (bottom)
        {0, 0, 1, D, H, 0, 0, 1, -3, 2, -1}, // WEST
        {1, 0, 1, W, H, 0, 0, 0, 1, 2, -3},  // NORTH (front)
        {1, 1, 1, D, H, 1, 0, 0, 3, 2, 1},   // EAST
        {2, 1, 1, W, H, 1, 0, 1, -1, 2, 3},  // SOUTH (back)
    };

    private VoxelMesh() {}

    /**
     * The six layer meshes of a skin. Only the alpha byte (bits 24-31) of {@code pixels} is read, so ARGB and NativeImage's ABGR
     * both work. Rows past {@code height} (legacy 64x32 skins) count as transparent.
     */
    static float[][] build(int[] pixels, int width, int height, boolean slim) {
        float[][] boxes = slim ? SLIM : WIDE;
        float[][] parts = new float[PARTS][];
        for (int part = 0; part < PARTS; part++)
            parts[part] = box(pixels, width, height, boxes[part], part == HAT ? HEAD_DEPTH : BODY_DEPTH, part * NUDGE);
        return parts;
    }

    private static float[] box(int[] pixels, int width, int height, float[] box, float depth, float nudge) {
        int[] dims = {(int) box[2], (int) box[3], (int) box[4]};
        float[] min = {box[5], box[6], box[7]}, max = {box[5] + dims[0], box[6] + dims[1], box[7] + dims[2]};
        float[] out = new float[64];
        int used = 0;
        for (int[] face : FACES) {
            int texU = (int) box[0] + face[0] * dims[D] + face[1] * dims[W], texV = (int) box[1] + face[2] * dims[D];
            int cols = dims[face[3]], rows = dims[face[4]];
            float[] origin = {face[5] == 0 ? min[0] : max[0], face[6] == 0 ? min[1] : max[1], face[7] == 0 ? min[2] : max[2]};
            int uDir = face[8], vDir = face[9], normal = face[10];
            for (int b = 0; b < rows; b++)
                for (int a = 0; a < cols; a++) {
                    int alpha = alpha(pixels, width, height, texU + a, texV + b);
                    if (alpha < MIN_ALPHA) continue;
                    float[] lo = origin.clone(), hi = origin.clone();
                    step(lo, uDir, a); step(lo, vDir, b);
                    step(hi, uDir, a + 1); step(hi, vDir, b + 1);
                    order(lo, hi);
                    grow(lo, hi, normal, depth);
                    boolean grown = false;
                    if (a == 0 && owns(normal, -uDir)) { grow(lo, hi, -uDir, depth); grown = true; }
                    if (a == cols - 1 && owns(normal, uDir)) { grow(lo, hi, uDir, depth); grown = true; }
                    if (b == 0 && owns(normal, -vDir)) { grow(lo, hi, -vDir, depth); grown = true; }
                    if (b == rows - 1 && owns(normal, vDir)) { grow(lo, hi, vDir, depth); grown = true; }
                    float u = (texU + a + 0.5f) / width, v = (texV + b + 0.5f) / height;
                    for (int dir : new int[] {1, -1, 2, -2, 3, -3}) {
                        boolean draw;
                        if (dir == normal) draw = true;
                        else if (dir == -normal) draw = grown; // a plain cuboid's inner face lies on the base box, never seen
                        else if (dir == uDir || dir == -uDir) draw = !opaque(pixels, width, height, texU, texV, cols, rows, a + (dir == uDir ? 1 : -1), b);
                        else draw = !opaque(pixels, width, height, texU, texV, cols, rows, a, b + (dir == vDir ? 1 : -1));
                        if (!draw) continue;
                        if (used + FLOATS > out.length) out = Arrays.copyOf(out, out.length * 2);
                        quad(out, used, lo, hi, dir, u, v, nudge);
                        used += FLOATS;
                    }
                }
        }
        return Arrays.copyOf(out, used);
    }

    private static int alpha(int[] pixels, int width, int height, int x, int y) {
        return x < 0 || y < 0 || x >= width || y >= height ? 0 : pixels[y * width + x] >>> 24;
    }

    /** A neighbour texel of the same face is fully opaque (texels past the face's edge belong to another face: not counted). */
    private static boolean opaque(int[] pixels, int width, int height, int texU, int texV, int cols, int rows, int a, int b) {
        return a >= 0 && b >= 0 && a < cols && b < rows && alpha(pixels, width, height, texU + a, texV + b) == 255;
    }

    /** Whether the face with outward normal {@code normal} covers the corner on its side {@code toward} (see the class doc). */
    private static boolean owns(int normal, int toward) {
        if (Math.abs(normal) == 2) return true;
        if (Math.abs(toward) == 2) return false;
        return toward == clockwise(normal);
    }

    /** The side after this one going round the part: north (-z), east (+x), south (+z), west (-x). */
    private static int clockwise(int normal) {
        return switch (normal) { case -3 -> 1; case 1 -> 3; case 3 -> -1; default -> -3; };
    }

    private static void step(float[] point, int dir, int amount) {
        point[Math.abs(dir) - 1] += Math.signum(dir) * amount;
    }

    private static void order(float[] lo, float[] hi) {
        for (int i = 0; i < 3; i++)
            if (lo[i] > hi[i]) { float swap = lo[i]; lo[i] = hi[i]; hi[i] = swap; }
    }

    private static void grow(float[] lo, float[] hi, int dir, float amount) {
        if (dir > 0) hi[dir - 1] += amount; else lo[-dir - 1] -= amount;
    }

    /** The cuboid face toward {@code dir}, counter-clockwise from outside: tangents a, b with a x b = the normal. */
    private static void quad(float[] out, int at, float[] lo, float[] hi, int dir, float u, float v, float nudge) {
        int axis = Math.abs(dir) - 1;
        int ta, tb;
        switch (dir) {
            case 1 -> { ta = 1; tb = 2; }
            case -1 -> { ta = 2; tb = 1; }
            case 2 -> { ta = 2; tb = 0; }
            case -2 -> { ta = 0; tb = 2; }
            case 3 -> { ta = 0; tb = 1; }
            default -> { ta = 1; tb = 0; }
        }
        float plane = dir > 0 ? hi[axis] : lo[axis];
        float[][] corners = {{lo[ta], lo[tb]}, {hi[ta], lo[tb]}, {hi[ta], hi[tb]}, {lo[ta], hi[tb]}};
        for (int c = 0; c < 4; c++) {
            float[] p = new float[3];
            p[axis] = plane;
            p[ta] = corners[c][0];
            p[tb] = corners[c][1];
            for (int i = 0; i < 3; i++) out[at + c * 3 + i] = (p[i] + nudge) / 16f;
        }
        out[at + 12] = u;
        out[at + 13] = v;
        out[at + 14] = axis == 0 ? Math.signum(dir) : 0;
        out[at + 15] = axis == 1 ? Math.signum(dir) : 0;
        out[at + 16] = axis == 2 ? Math.signum(dir) : 0;
    }

    /** Self-check (run with -ea against the compiled classes): counts, winding and legacy skins. */
    public static void main(String[] args) {
        int[] solid = new int[64 * 64];
        Arrays.fill(solid, 0xFF000000);
        float[][] full = build(solid, 64, 64, false);
        // hat, all opaque: 6 faces x 64 outer faces, 6 x 32 border sides, and the inner face of every grown cuboid
        // (top + bottom: 28 border texels each; 4 sides: one owned column of 8) = 384 + 192 + 88
        check(quads(full[HAT]) == 664, "solid hat quads " + quads(full[HAT]));
        check(quads(build(new int[64 * 64], 64, 64, false)[JACKET]) == 0, "transparent skin has no cuboids");
        check(quads(build(solid, 64, 64, true)[LEFT_SLEEVE]) < quads(full[LEFT_SLEEVE]), "slim sleeves are smaller");
        float[][] legacy = build(solid, 64, 32, false); // legacy 64x32 skins: only the hat exists
        check(quads(legacy[HAT]) == quads(full[HAT]), "legacy skin keeps its hat");
        for (int part = JACKET; part < PARTS; part++) check(quads(legacy[part]) == 0, "legacy skin has no outer layer " + part);

        int[] one = new int[64 * 64];
        one[(8 + 3) * 64 + 32 + 8 + 3] = 0xFFFFFFFF; // a texel in the middle of the hat's front (north) face
        float[] lone = build(one, 64, 64, false)[HAT];
        check(quads(lone) == 5, "a lone cuboid has 5 faces, not its inner one: " + quads(lone));
        boolean front = false;
        for (int q = 0; q < lone.length; q += FLOATS)
            if (lone[q + 16] == -1) front = Math.abs(lone[q + 2] * 16 - (-4 - HEAD_DEPTH)) < 1e-4;
        check(front, "the front face sits HEAD_DEPTH in front of the head");

        int[] checker = new int[64 * 64];
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) if ((x + y) % 2 == 0) checker[y * 64 + x] = 0xFFFFC000;
        for (float[] part : build(checker, 64, 64, false)) {
            check(part.length > 0, "checkerboard gives cuboids on every part");
            for (int q = 0; q < part.length; q += FLOATS) {
                float ax = part[q + 3] - part[q], ay = part[q + 4] - part[q + 1], az = part[q + 5] - part[q + 2];
                float bx = part[q + 6] - part[q + 3], by = part[q + 7] - part[q + 4], bz = part[q + 8] - part[q + 5];
                float dot = (ay * bz - az * by) * part[q + 14] + (az * bx - ax * bz) * part[q + 15] + (ax * by - ay * bx) * part[q + 16];
                check(dot > 0, "every quad is counter-clockwise seen from outside");
            }
        }
        System.out.println("VoxelMesh self-check passed");
    }

    private static int quads(float[] part) { return part.length / FLOATS; }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
