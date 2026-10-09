package com.thelads.core.perf;

/**
 * The GPU's back-face culling done on the CPU (1.8.5, Lads remake of Flerovium's item culling): a quad the GPU would throw away
 * anyway (clockwise on screen, or covering no area) is never transformed, lit and written. Only for render types that cull, so
 * the picture stays identical: the winding is decided with the same matrices the GPU uses, so it agrees with it under any
 * mirroring, flip or projection. Quads nearly edge-on, or with a corner behind the camera, are left to the GPU.
 * <p>
 * Per draw, {@link #prepare} reduces the clip matrix to the centre of projection in model space (the four signed 3x3 minors of its
 * x, y and w rows: a point for a perspective, a direction for an orthographic projection). A planar quad's screen winding is then
 * the sign of one dot product with its model-space normal, so a quad costs a few multiplications instead of four projected corners.
 * Bent or degenerate quads take the full projected test.
 */
public final class FaceCull {
    public static final int KEEP = 0, CULL = 1, LOD = 2;
    /** The matrix arrays callers pass hold the 16 clip matrix floats followed by the 4 floats {@link #prepare} adds. */
    public static final int MATRIX_FLOATS = 20;
    /** Relative tolerance of the winding tests: a quad this close to edge-on is kept and left to the GPU. */
    private static final float TOLERANCE = 2e-4f;
    /** Out of plane by more than this (relative), a quad is bent: projected test. */
    private static final float PLANAR = 1e-4f;

    private FaceCull() {}

    /**
     * Fills m[16..19] with the centre of projection of the clip matrix m[0..15] in model space: c . (n, -n.p) is the screen winding
     * of a plane through p with normal n, times the product of its corners' w. Call once per matrix, before {@link #test}.
     */
    public static float[] prepare(float[] m) {
        // Columns of the 3x4 matrix of the x, y and w rows: (m[4j], m[4j+1], m[4j+3]) for column j.
        float a0 = m[0], a1 = m[1], a3 = m[3], b0 = m[4], b1 = m[5], b3 = m[7];
        float c0 = m[8], c1 = m[9], c3 = m[11], d0 = m[12], d1 = m[13], d3 = m[15];
        m[16] = det3(b0, b1, b3, c0, c1, c3, d0, d1, d3);
        m[17] = -det3(a0, a1, a3, c0, c1, c3, d0, d1, d3);
        m[18] = det3(a0, a1, a3, b0, b1, b3, d0, d1, d3);
        m[19] = -det3(a0, a1, a3, b0, b1, b3, c0, c1, c3);
        return m;
    }

    private static float det3(float a0, float a1, float a2, float b0, float b1, float b2, float c0, float c1, float c2) {
        return a0 * (b1 * c2 - b2 * c1) - b0 * (a1 * c2 - a2 * c1) + c0 * (a1 * b2 - a2 * b1);
    }

    /**
     * KEEP, CULL (back-facing or no area) or LOD (front-facing but smaller on screen than {@code lodPixels}; 0 turns LOD off).
     * {@code m}: viewport scale x projection x model-view x pose, column-major as JOML's {@code get(float[])} (x/w and y/w in
     * pixels), then {@link #prepare}d.
     */
    public static int test(float[] m, float x0, float y0, float z0, float x1, float y1, float z1,
                           float x2, float y2, float z2, float x3, float y3, float z3, float lodPixels) {
        if (!(m[3] * x0 + m[7] * y0 + m[11] * z0 + m[15] > 0 && m[3] * x1 + m[7] * y1 + m[11] * z1 + m[15] > 0
            && m[3] * x2 + m[7] * y2 + m[11] * z2 + m[15] > 0 && m[3] * x3 + m[7] * y3 + m[11] * z3 + m[15] > 0))
            return KEEP; // clipped by the near plane: the GPU decides
        // Normal from the diagonals (twice the area vector of a planar quad, its orientation the corners' order).
        float px = x2 - x0, py = y2 - y0, pz = z2 - z0, qx = x3 - x1, qy = y3 - y1, qz = z3 - z1;
        float nx = py * qz - pz * qy, ny = pz * qx - px * qz, nz = px * qy - py * qx;
        float n = Math.abs(nx) + Math.abs(ny) + Math.abs(nz);
        float ex = x1 - x0, ey = y1 - y0, ez = z1 - z0;
        float e = Math.abs(ex) + Math.abs(ey) + Math.abs(ez);
        if (!(n > 0) || Math.abs(nx * ex + ny * ey + nz * ez) > PLANAR * n * e)
            return projected(m, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, lodPixels); // bent, or no area in model space
        float cx = m[16], cy = m[17], cz = m[18], cw = m[19];
        float tx = nx * cx, ty = ny * cy, tz = nz * cz, d = nx * x0 + ny * y0 + nz * z0;
        float winding = tx + ty + tz - cw * d;
        float scale = Math.abs(tx) + Math.abs(ty) + Math.abs(tz) + Math.abs(cw) * (Math.abs(nx * x0) + Math.abs(ny * y0) + Math.abs(nz * z0));
        if (winding == 0 || winding < -TOLERANCE * scale) return CULL;
        if (lodPixels > 0 && winding > TOLERANCE * scale) return projected(m, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, lodPixels);
        return KEEP;
    }

    /** {@link #test} on a baked quad's packed vertices: position floats first in each vertex of {@code stride} ints. */
    public static int test(float[] m, int[] vertices, int stride, float lodPixels) {
        return test(m,
            Float.intBitsToFloat(vertices[0]), Float.intBitsToFloat(vertices[1]), Float.intBitsToFloat(vertices[2]),
            Float.intBitsToFloat(vertices[stride]), Float.intBitsToFloat(vertices[stride + 1]), Float.intBitsToFloat(vertices[stride + 2]),
            Float.intBitsToFloat(vertices[2 * stride]), Float.intBitsToFloat(vertices[2 * stride + 1]), Float.intBitsToFloat(vertices[2 * stride + 2]),
            Float.intBitsToFloat(vertices[3 * stride]), Float.intBitsToFloat(vertices[3 * stride + 1]), Float.intBitsToFloat(vertices[3 * stride + 2]),
            lodPixels);
    }

    /** The full test: corners projected to the screen, both diagonal splits must face away; on-screen area for LOD. */
    static int projected(float[] m, float x0, float y0, float z0, float x1, float y1, float z1,
                         float x2, float y2, float z2, float x3, float y3, float z3, float lodPixels) {
        float w0 = m[3] * x0 + m[7] * y0 + m[11] * z0 + m[15];
        float w1 = m[3] * x1 + m[7] * y1 + m[11] * z1 + m[15];
        float w2 = m[3] * x2 + m[7] * y2 + m[11] * z2 + m[15];
        float w3 = m[3] * x3 + m[7] * y3 + m[11] * z3 + m[15];
        if (!(w0 > 0 && w1 > 0 && w2 > 0 && w3 > 0)) return KEEP;
        // Screen positions; w is exactly 1 under an orthographic projection, so axis-aligned edges stay exactly collinear.
        float sx0 = (m[0] * x0 + m[4] * y0 + m[8] * z0 + m[12]) / w0, sy0 = (m[1] * x0 + m[5] * y0 + m[9] * z0 + m[13]) / w0;
        float sx1 = (m[0] * x1 + m[4] * y1 + m[8] * z1 + m[12]) / w1, sy1 = (m[1] * x1 + m[5] * y1 + m[9] * z1 + m[13]) / w1;
        float sx2 = (m[0] * x2 + m[4] * y2 + m[8] * z2 + m[12]) / w2, sy2 = (m[1] * x2 + m[5] * y2 + m[9] * z2 + m[13]) / w2;
        float sx3 = (m[0] * x3 + m[4] * y3 + m[8] * z3 + m[12]) / w3, sy3 = (m[1] * x3 + m[5] * y3 + m[9] * z3 + m[13]) / w3;
        if (back(sx0, sy0, sx1, sy1, sx2, sy2) && back(sx0, sy0, sx2, sy2, sx3, sy3)
            && back(sx0, sy0, sx1, sy1, sx3, sy3) && back(sx1, sy1, sx2, sy2, sx3, sy3)) return CULL;
        if (lodPixels > 0) {
            float area = 0.5f * Math.abs((sx0 * sy1 - sx1 * sy0) + (sx1 * sy2 - sx2 * sy1) + (sx2 * sy3 - sx3 * sy2) + (sx3 * sy0 - sx0 * sy3));
            if (area < lodPixels) return LOD;
        }
        return KEEP;
    }

    /** Counter-clockwise is the front (OpenGL's default); clockwise or no area at all is culled; nearly edge-on is not decided here. */
    private static boolean back(float ax, float ay, float bx, float by, float cx, float cy) {
        float p = (bx - ax) * (cy - ay), q = (by - ay) * (cx - ax);
        float area = p - q;
        return area == 0 || area < -TOLERANCE * (Math.abs(p) + Math.abs(q));
    }

    /** Scales clip-space x and y of {@code m} in place so that x/w and y/w are pixels. */
    public static float[] toViewport(float[] m, float viewportWidth, float viewportHeight) {
        for (int col = 0; col < 4; col++) {
            m[col * 4] *= viewportWidth * 0.5f;
            m[col * 4 + 1] *= viewportHeight * 0.5f;
        }
        return m;
    }
}
