package com.thelads.core.client.ebe;

/**
 * Turns a quad captured from an entity-style model (four vertices in block space, front face counter-clockwise) into
 * the shape vanilla's block pipeline expects: the face direction, and the vertex order its baked block quads use
 * (vanilla's smooth-lighting code reads corner brightness by vertex index, so a rotated order shades the wrong corners).
 * Direction ordinals follow Minecraft's Direction/EnumFacing: DOWN, UP, NORTH, SOUTH, WEST, EAST.
 */
public final class BakedQuadOrder {
    private BakedQuadOrder() {}

    /** In-plane axes per direction (0=x, 1=y, 2=z). */
    private static final int[][] AXES = {{0, 2}, {0, 2}, {0, 1}, {0, 1}, {2, 1}, {2, 1}};
    /** Per direction and slot: is the vertex on the max side of the first / second in-plane axis (vanilla FaceInfo). */
    private static final boolean[][][] CORNERS = {
        {{false, true}, {false, false}, {true, false}, {true, true}},   // DOWN
        {{false, false}, {false, true}, {true, true}, {true, false}},   // UP
        {{true, true}, {true, false}, {false, false}, {false, true}},   // NORTH
        {{false, true}, {false, false}, {true, false}, {true, true}},   // SOUTH
        {{false, true}, {false, false}, {true, false}, {true, true}},   // WEST
        {{true, true}, {true, false}, {false, false}, {false, true}},   // EAST
    };

    /** Twice the quad's area (x,y,z per vertex); zero for the edge faces of flat cubes, which are never visible. */
    public static float area2(float[] p) {
        float[] n = normal(p);
        return (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
    }

    /** Unnormalised face normal from the diagonals: (v2 - v0) x (v3 - v1). */
    public static float[] normal(float[] p) {
        float ax = p[6] - p[0], ay = p[7] - p[1], az = p[8] - p[2];
        float bx = p[9] - p[3], by = p[10] - p[4], bz = p[11] - p[5];
        return new float[] {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    /** Nearest axis direction of the face normal. */
    public static int direction(float[] p) {
        float[] n = normal(p);
        float ax = Math.abs(n[0]), ay = Math.abs(n[1]), az = Math.abs(n[2]);
        if (ay >= ax && ay >= az) return n[1] < 0 ? 0 : 1;
        if (az >= ax) return n[2] < 0 ? 2 : 3;
        return n[0] < 0 ? 4 : 5;
    }

    /**
     * order[slot] = source vertex for vanilla's slot order of {@code direction}; the identity when the corners are
     * ambiguous (heavily rotated quads), which only costs exact smooth-lighting corners.
     */
    public static int[] order(float[] p, int direction) {
        int a = AXES[direction][0], b = AXES[direction][1];
        float ca = (p[a] + p[3 + a] + p[6 + a] + p[9 + a]) / 4f;
        float cb = (p[b] + p[3 + b] + p[6 + b] + p[9 + b]) / 4f;
        int[] order = {-1, -1, -1, -1};
        for (int v = 0; v < 4; v++) {
            boolean maxA = p[v * 3 + a] > ca, maxB = p[v * 3 + b] > cb;
            for (int slot = 0; slot < 4; slot++) {
                if (CORNERS[direction][slot][0] == maxA && CORNERS[direction][slot][1] == maxB) {
                    if (order[slot] != -1) return new int[] {0, 1, 2, 3};
                    order[slot] = v;
                }
            }
        }
        for (int slot : order) if (slot == -1) return new int[] {0, 1, 2, 3};
        return order;
    }
}
