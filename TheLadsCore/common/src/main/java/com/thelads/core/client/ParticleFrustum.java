package com.thelads.core.client;

/**
 * The view frustum Particle Core tests particles against: six normalised planes of projection * view, in camera-relative space
 * (the space particles are drawn in, so no world offset is needed). One instance per game, set once per frame by the adapter and
 * read per particle: no allocation, no Minecraft types, so every version shares the maths and its unit test.
 */
public final class ParticleFrustum {
    private final double[] planes = new double[24];
    private boolean valid;

    /** Both matrices are 4x4, column-major (OpenGL, JOML and LWJGL order); the planes are those of {@code projection * view}. */
    public void set(float[] projection, float[] view) {
        double[] clip = new double[16];
        for (int col = 0; col < 4; col++)
            for (int row = 0; row < 4; row++) {
                double sum = 0;
                for (int k = 0; k < 4; k++) sum += (double) projection[k * 4 + row] * view[col * 4 + k];
                clip[col * 4 + row] = sum;
            }
        // Row i of the clip matrix is clip[i], clip[4 + i], clip[8 + i], clip[12 + i]; a point is inside when -w <= x, y, z <= w.
        valid = plane(0, clip, 0, 1) & plane(1, clip, 0, -1) & plane(2, clip, 1, 1) & plane(3, clip, 1, -1)
            & plane(4, clip, 2, 1) & plane(5, clip, 2, -1);
    }

    private boolean plane(int index, double[] clip, int row, int sign) {
        double a = clip[3] + sign * clip[row], b = clip[7] + sign * clip[4 + row], c = clip[11] + sign * clip[8 + row], d = clip[15] + sign * clip[12 + row];
        double length = Math.sqrt(a * a + b * b + c * c);
        if (!(length > 1e-9) || Double.isNaN(d)) return false; // a degenerate matrix: never cull
        planes[index * 4] = a / length;
        planes[index * 4 + 1] = b / length;
        planes[index * 4 + 2] = c / length;
        planes[index * 4 + 3] = d / length;
        return true;
    }

    /** True once {@link #set} has seen a usable pair of matrices; false again after {@link #clear}. */
    public boolean isValid() { return valid; }

    /** Between frames (and for any view this frustum was not made for) nothing is culled. */
    public void clear() { valid = false; }

    /** False only when the sphere is wholly outside one plane; without a valid frustum every sphere is visible. */
    public boolean visible(double x, double y, double z, double radius) {
        if (!valid) return true;
        for (int i = 0; i < 24; i += 4)
            if (planes[i] * x + planes[i + 1] * y + planes[i + 2] * z + planes[i + 3] < -radius) return false;
        return true;
    }

    /**
     * Like {@link #visible} for an upright cylinder (a weather column): centre camera-relative, {@code radius} across, {@code halfHeight}
     * up and down. Its reach towards a plane's normal is radius * |normal.xz| + halfHeight * |normal.y|.
     */
    public boolean visibleCylinder(double x, double y, double z, double radius, double halfHeight) {
        if (!valid) return true;
        for (int i = 0; i < 24; i += 4) {
            double reach = radius * Math.sqrt(planes[i] * planes[i] + planes[i + 2] * planes[i + 2]) + Math.abs(planes[i + 1]) * halfHeight;
            if (planes[i] * x + planes[i + 1] * y + planes[i + 2] * z + planes[i + 3] < -reach) return false;
        }
        return true;
    }
}
