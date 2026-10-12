package com.thelads.core.client;

import java.util.List;

/**
 * The outer skin layers (hat, jacket, sleeves, trousers) in 3D, game-independent: every pixel of a flat layer face that is not
 * (nearly) transparent becomes a small box standing out of it, as the Skin Customization preview draws them (26.3's PreviewSkinLayers).
 */
public final class LayerVoxels {
    /** How far a box stands out of the flat layer, in skin pixels. */
    public static final float OUT = 0.35f;

    /** One skin pixel as a box: its corner and size in model units, and the pixel (px, py) its faces show. */
    public record Box(float x, float y, float z, float dx, float dy, float dz, int px, int py) {}

    private LayerVoxels() {}

    /**
     * The boxes of one flat face of a skin box.
     *
     * @param face   four corners {x, y, z, u, v}, in this order around the face (u and v are skin pixels): the other three are
     *               reached from the first along corner 1 and corner 3, whatever the face's orientation or mirroring
     * @param center the middle of the skin box the face belongs to: the side the face looks away from is the outside
     * @param argb   the skin, width x height pixels
     */
    public static void extrude(float[][] face, float[] center, int[] argb, int width, int height, List<Box> out) {
        float[] p0 = face[0], p1 = face[1], p3 = face[3];
        float du1 = p1[3] - p0[3], dv1 = p1[4] - p0[4], du2 = p3[3] - p0[3], dv2 = p3[4] - p0[4];
        float det = du1 * dv2 - dv1 * du2;
        if (Math.abs(det) < 1e-6f) return;
        float[] normal = outward(face, center);
        int minU = Integer.MAX_VALUE, minV = Integer.MAX_VALUE, maxU = 0, maxV = 0;
        for (float[] corner : face) {
            minU = Math.min(minU, Math.round(corner[3]));
            maxU = Math.max(maxU, Math.round(corner[3]));
            minV = Math.min(minV, Math.round(corner[4]));
            maxV = Math.max(maxV, Math.round(corner[4]));
        }
        for (int py = Math.max(0, minV); py < Math.min(height, maxV); py++)
            for (int px = Math.max(0, minU); px < Math.min(width, maxU); px++) {
                if ((argb[py * width + px] >>> 24) < 16) continue;
                float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                for (int cornerU = 0; cornerU <= 1; cornerU++)
                    for (int cornerV = 0; cornerV <= 1; cornerV++) {
                        float u = px + cornerU - p0[3], v = py + cornerV - p0[4];
                        float s = (u * dv2 - v * du2) / det, t = (du1 * v - dv1 * u) / det; // the corner as p0 + s (p1 - p0) + t (p3 - p0)
                        for (int axis = 0; axis < 3; axis++) {
                            float point = p0[axis] + s * (p1[axis] - p0[axis]) + t * (p3[axis] - p0[axis]);
                            lo[axis] = Math.min(lo[axis], point + Math.min(0, normal[axis] * OUT));
                            hi[axis] = Math.max(hi[axis], point + Math.max(0, normal[axis] * OUT));
                        }
                    }
                out.add(new Box(lo[0], lo[1], lo[2], hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2], px, py));
            }
    }

    /** The face's unit normal, the one pointing away from the middle of its box. */
    static float[] outward(float[][] face, float[] center) {
        float ax = face[1][0] - face[0][0], ay = face[1][1] - face[0][1], az = face[1][2] - face[0][2];
        float bx = face[3][0] - face[0][0], by = face[3][1] - face[0][1], bz = face[3][2] - face[0][2];
        float[] normal = {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
        float length = (float) Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
        float away = 0;
        for (int axis = 0; axis < 3; axis++) {
            normal[axis] /= length;
            away += normal[axis] * ((face[0][axis] + face[2][axis]) / 2 - center[axis]);
        }
        if (away < 0) for (int axis = 0; axis < 3; axis++) normal[axis] = -normal[axis];
        return normal;
    }
}
