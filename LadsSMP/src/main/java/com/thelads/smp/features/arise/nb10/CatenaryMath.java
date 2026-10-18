package com.thelads.smp.features.arise.nb10;

import net.minecraft.world.phys.Vec3;

/**
 * Pure mathematics helper for catenary sag curves and wind sway dynamics.
 * Supports zero-allocation evaluation using pre-allocated float/double buffers.
 */
public final class CatenaryMath {

    private CatenaryMath() {}

    /**
     * Calculates the sag depth (in blocks) given the horizontal span and slack multiplier.
     */
    public static float getSagDepth(float horizontalDist, float slack) {
        if (horizontalDist < 0.01F) return 0.05F * slack;
        float baseSag = horizontalDist * 0.085F * slack;
        // Clamp minimum sag to 0.12 blocks and maximum sag to 2.8 blocks
        return Math.max(0.12F, Math.min(2.8F, baseSag));
    }

    /**
     * Calculates a single 3D point along the hanging catenary curve at parameter t in [0.0, 1.0].
     */
    public static Vec3 getPoint(Vec3 p0, Vec3 p1, float slack, float t) {
        double dx = p1.x - p0.x;
        double dy = p1.y - p0.y;
        double dz = p1.z - p0.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float sag = getSagDepth((float) horizDist, slack);

        double x = p0.x + t * dx;
        double z = p0.z + t * dz;
        // Parabolic-catenary sag: maximum at t = 0.5, zero at endpoints t = 0 and t = 1
        double sagY = -4.0 * sag * t * (1.0 - t);
        double y = p0.y + t * dy + sagY;

        return new Vec3(x, y, z);
    }

    /**
     * Calculates a single 3D point along the hanging curve including wind sway displacement.
     */
    public static Vec3 getPointWithWind(Vec3 p0, Vec3 p1, float slack, float t, float timeTicks, float phase) {
        Vec3 base = getPoint(p0, p1, slack, t);

        double dx = p1.x - p0.x;
        double dz = p1.z - p0.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        if (horizDist < 0.01) {
            return base;
        }

        // Horizontal normal vector perpendicular to span
        double nx = -dz / horizDist;
        double nz = dx / horizDist;

        float sag = getSagDepth((float) horizDist, slack);
        // Wind displacement: anchored at ends via sin(pi * t), oscillating over time
        double sway = Math.sin(Math.PI * t) * Math.sin(0.06 * timeTicks + phase) * 0.10 * sag;

        return new Vec3(base.x + nx * sway, base.y, base.z + nz * sway);
    }

    /**
     * Fills pre-allocated coordinate arrays for N segments along the catenary curve without heap allocations.
     * Output arrays must have length at least (segments + 1).
     */
    public static void computeCurvePoints(
            double x0, double y0, double z0,
            double x1, double y1, double z1,
            float slack, int segments,
            float[] outX, float[] outY, float[] outZ
    ) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float sag = getSagDepth((float) horizDist, slack);

        float invSegments = 1.0F / segments;
        for (int i = 0; i <= segments; i++) {
            float t = i * invSegments;
            double sagY = -4.0 * sag * t * (1.0F - t);
            outX[i] = (float) (x0 + t * dx);
            outY[i] = (float) (y0 + t * dy + sagY);
            outZ[i] = (float) (z0 + t * dz);
        }
    }
}
