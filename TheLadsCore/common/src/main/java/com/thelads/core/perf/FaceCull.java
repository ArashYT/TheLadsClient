package com.thelads.core.perf;

/**
 * The GPU's back-face culling done on the CPU (1.8.5, Lads remake of Flerovium's item culling): a quad the GPU would throw away
 * anyway (clockwise on screen, or covering no area) is never transformed, lit and written. Only for render types that cull, so
 * the picture stays identical: the test runs in clip space with the same matrices the GPU uses, so it agrees with it under any
 * mirroring, flip or projection. Quads nearly edge-on, or with a corner behind the camera, are left to the GPU.
 */
public final class FaceCull {
    public static final int KEEP = 0, CULL = 1, LOD = 2;
    /** Relative tolerance of the winding test: a quad this close to edge-on is kept and left to the GPU. */
    private static final float TOLERANCE = 2e-4f;

    private FaceCull() {}

    /**
     * KEEP, CULL (back-facing or no area) or LOD (front-facing but smaller on screen than {@code lodPixels}; 0 turns LOD off).
     * {@code m}: viewport scale x projection x model-view x pose, column-major as JOML's {@code get(float[])}, so x/w and y/w
     * come out in pixels.
     */
    public static int test(float[] m, float x0, float y0, float z0, float x1, float y1, float z1,
                           float x2, float y2, float z2, float x3, float y3, float z3, float lodPixels) {
        float w0 = m[3] * x0 + m[7] * y0 + m[11] * z0 + m[15];
        float w1 = m[3] * x1 + m[7] * y1 + m[11] * z1 + m[15];
        float w2 = m[3] * x2 + m[7] * y2 + m[11] * z2 + m[15];
        float w3 = m[3] * x3 + m[7] * y3 + m[11] * z3 + m[15];
        if (!(w0 > 0 && w1 > 0 && w2 > 0 && w3 > 0)) return KEEP; // clipped by the near plane: the GPU decides
        // Screen positions; w is exactly 1 under an orthographic projection, so axis-aligned edges stay exactly collinear.
        float sx0 = (m[0] * x0 + m[4] * y0 + m[8] * z0 + m[12]) / w0, sy0 = (m[1] * x0 + m[5] * y0 + m[9] * z0 + m[13]) / w0;
        float sx1 = (m[0] * x1 + m[4] * y1 + m[8] * z1 + m[12]) / w1, sy1 = (m[1] * x1 + m[5] * y1 + m[9] * z1 + m[13]) / w1;
        float sx2 = (m[0] * x2 + m[4] * y2 + m[8] * z2 + m[12]) / w2, sy2 = (m[1] * x2 + m[5] * y2 + m[9] * z2 + m[13]) / w2;
        float sx3 = (m[0] * x3 + m[4] * y3 + m[8] * z3 + m[12]) / w3, sy3 = (m[1] * x3 + m[5] * y3 + m[9] * z3 + m[13]) / w3;
        // Both diagonals' triangle pairs, so a bent quad is culled only when every way of splitting it faces away.
        if (back(sx0, sy0, sx1, sy1, sx2, sy2) && back(sx0, sy0, sx2, sy2, sx3, sy3)
            && back(sx0, sy0, sx1, sy1, sx3, sy3) && back(sx1, sy1, sx2, sy2, sx3, sy3)) return CULL;
        if (lodPixels > 0) {
            float area = 0.5f * Math.abs((sx0 * sy1 - sx1 * sy0) + (sx1 * sy2 - sx2 * sy1) + (sx2 * sy3 - sx3 * sy2) + (sx3 * sy0 - sx0 * sy3));
            if (area < lodPixels) return LOD;
        }
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

    /** Counter-clockwise is the front (OpenGL's default); clockwise or no area at all is culled; nearly edge-on is not decided here. */
    private static boolean back(float ax, float ay, float bx, float by, float cx, float cy) {
        float p = (bx - ax) * (cy - ay), q = (by - ay) * (cx - ax);
        float area = p - q;
        return area == 0 || area < -TOLERANCE * (Math.abs(p) + Math.abs(q));
    }

    /** {@code out} = viewport scale (w/2, h/2) x a x b, column-major (16 floats each; {@code out} must be neither). */
    public static float[] clip(float[] out, float[] a, float[] b, float viewportWidth, float viewportHeight) {
        for (int col = 0; col < 4; col++)
            for (int row = 0; row < 4; row++)
                out[col * 4 + row] = a[row] * b[col * 4] + a[4 + row] * b[col * 4 + 1] + a[8 + row] * b[col * 4 + 2] + a[12 + row] * b[col * 4 + 3];
        return toViewport(out, viewportWidth, viewportHeight);
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
