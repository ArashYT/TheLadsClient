package com.thelads.smp.features.arise.nb10.client;

import com.thelads.smp.features.arise.nb10.CatenaryMath;
import com.thelads.smp.features.arise.nb10.DecorationType;

/**
 * Pre-allocated static geometry for a hanging connection.
 * Avoids per-frame allocations during rendering.
 */
public final class CachedConnectionMesh {

    public final int segments;
    public final float[] x;
    public final float[] y;
    public final float[] z;

    public final int bulbCount;
    public final float[] bulbX;
    public final float[] bulbY;
    public final float[] bulbZ;
    public final int[] bulbColors;

    public CachedConnectionMesh(int segments, int bulbCount) {
        this.segments = segments;
        this.x = new float[segments + 1];
        this.y = new float[segments + 1];
        this.z = new float[segments + 1];

        this.bulbCount = bulbCount;
        this.bulbX = new float[bulbCount];
        this.bulbY = new float[bulbCount];
        this.bulbZ = new float[bulbCount];
        this.bulbColors = new int[bulbCount];
    }

    public static CachedConnectionMesh build(
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float slack, DecorationType type, int[] colors
    ) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Adjust segment count dynamically based on span length: 8 to 32 segments
        int segs = Math.max(8, Math.min(32, (int) Math.ceil(length * 2.0)));
        // Equidistant bulb/pennant spacing along cord (~0.65m spacing)
        int bulbs = Math.max(1, (int) (length / 0.65));

        CachedConnectionMesh mesh = new CachedConnectionMesh(segs, bulbs);

        // Pre-compute catenary curve vertices
        CatenaryMath.computeCurvePoints(x0, y0, z0, x1, y1, z1, slack, segs, mesh.x, mesh.y, mesh.z);

        // Pre-compute bulb / pennant anchor positions
        float invBulbs = 1.0F / (bulbs + 1);
        for (int i = 0; i < bulbs; i++) {
            float t = (i + 1) * invBulbs;
            // Interpolate along the curve
            float segIndexF = t * segs;
            int segIndex = Math.min((int) segIndexF, segs - 1);
            float frac = segIndexF - segIndex;

            mesh.bulbX[i] = mesh.x[segIndex] + frac * (mesh.x[segIndex + 1] - mesh.x[segIndex]);
            mesh.bulbY[i] = mesh.y[segIndex] + frac * (mesh.y[segIndex + 1] - mesh.y[segIndex]);
            mesh.bulbZ[i] = mesh.z[segIndex] + frac * (mesh.z[segIndex + 1] - mesh.z[segIndex]);

            // Cycle colors across bulbs
            mesh.bulbColors[i] = (colors != null && colors.length > 0) ? colors[i % colors.length] : 0xFFE484;
        }

        return mesh;
    }
}
