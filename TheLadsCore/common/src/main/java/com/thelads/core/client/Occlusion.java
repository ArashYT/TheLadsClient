package com.thelads.core.client;

/**
 * Entity culling's visibility test against a grid of opaque block cells (the 1.8.9 culling thread feeds it the client world).
 * Conservative: a box is hidden only when every segment from every eye sample to every sample on the box faces turned to the
 * eye crosses an opaque cell. Eye samples are the eye and the corners of a cube around it, so the answer still holds while the
 * camera stays inside that cube (no pop-in when peeking round a corner before the next pass).
 */
public final class Occlusion {
    /** Whether a cell is an opaque full cube. Must tolerate any coordinates (unknown means not opaque). */
    public interface Grid { boolean opaque(int x, int y, int z); }

    /** Sample spacing on the box faces: an opening in a grid of full cubes is at least one block wide. */
    static final double STEP = 0.5;
    /** Erosion of opaque edges: at least half a sample cell's diagonal (0.354 for STEP 0.5). */
    static final double DEPTH = 0.375;
    private static final double INSIDE = 1e-4;

    private Occlusion() {}

    /**
     * Fills {@code out} (27 doubles) with the eye and those corners of the cube of half-size {@code r} around it that are in open
     * cells; a corner inside an opaque cell is pulled into the eye's own cell (the camera cannot be inside a wall). Returns how
     * many samples there are; 0 when the eye itself is inside an opaque cell (then nothing may be culled).
     */
    public static int eyes(Grid grid, double x, double y, double z, double r, double[] out) {
        int cx = floor(x), cy = floor(y), cz = floor(z);
        if (grid.opaque(cx, cy, cz)) return 0;
        out[0] = x; out[1] = y; out[2] = z;
        int n = 1;
        for (int i = 0; i < 8; i++) {
            double px = x + ((i & 1) == 0 ? -r : r), py = y + ((i & 2) == 0 ? -r : r), pz = z + ((i & 4) == 0 ? -r : r);
            if (grid.opaque(floor(px), floor(py), floor(pz))) {
                px = clamp(px, cx + INSIDE, cx + 1 - INSIDE);
                py = clamp(py, cy + INSIDE, cy + 1 - INSIDE);
                pz = clamp(pz, cz + INSIDE, cz + 1 - INSIDE);
            }
            out[n * 3] = px; out[n * 3 + 1] = py; out[n * 3 + 2] = pz;
            n++;
        }
        return n;
    }

    /** True when some eye sample sees some sample of the box faces turned to it (or an eye is inside the box). */
    public static boolean visible(Grid grid, double[] eyes, int eyeCount, double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        for (int e = 0; e < eyeCount; e++) {
            double x = eyes[e * 3], y = eyes[e * 3 + 1], z = eyes[e * 3 + 2];
            boolean inX = x > minX && x < maxX, inY = y > minY && y < maxY, inZ = z > minZ && z < maxZ;
            if (inX && inY && inZ) return true;
            int nx = count(maxX - minX), ny = count(maxY - minY), nz = count(maxZ - minZ);
            // Every line into the box enters through a face turned to the eye: sample those faces only.
            if (!inX && face(grid, x, y, z, 0, x < minX ? minX : maxX, minY, maxY, ny, minZ, maxZ, nz)) return true;
            if (!inY && face(grid, x, y, z, 1, y < minY ? minY : maxY, minX, maxX, nx, minZ, maxZ, nz)) return true;
            if (!inZ && face(grid, x, y, z, 2, z < minZ ? minZ : maxZ, minX, maxX, nx, minY, maxY, ny)) return true;
        }
        return false;
    }

    /** A face at {@code at} on {@code axis}, spanning (a0..a1, b0..b1) on the other two axes in order x, y, z. */
    private static boolean face(Grid grid, double x, double y, double z, int axis, double at,
                                double a0, double a1, int na, double b0, double b1, int nb) {
        for (int i = 0; i < na; i++) {
            double a = a0 + (a1 - a0) * i / (na - 1);
            for (int j = 0; j < nb; j++) {
                double b = b0 + (b1 - b0) * j / (nb - 1);
                double px = axis == 0 ? at : a, py = axis == 1 ? at : axis == 0 ? a : b, pz = axis == 2 ? at : b;
                if (!blocked(grid, x, y, z, px, py, pz)) return true;
            }
        }
        return false;
    }

    /**
     * Whether the segment is blocked: it crosses an opaque cell (after the one it starts in) deep inside the opaque mass, at
     * least {@link #DEPTH} from any face shared with an open cell. Eroding the edges this way makes a sight line that slips past
     * an edge between two face samples still count as seen through the nearest sample's line.
     */
    static boolean blocked(Grid grid, double x0, double y0, double z0, double x1, double y1, double z1) {
        int x = floor(x0), y = floor(y0), z = floor(z0);
        int steps = Math.abs(floor(x1) - x) + Math.abs(floor(y1) - y) + Math.abs(floor(z1) - z);
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tdx = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dx);
        double tdy = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dy);
        double tdz = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1 / dz);
        double tx = dx == 0 ? Double.POSITIVE_INFINITY : (dx > 0 ? x + 1 - x0 : x0 - x) * tdx;
        double ty = dy == 0 ? Double.POSITIVE_INFINITY : (dy > 0 ? y + 1 - y0 : y0 - y) * tdy;
        double tz = dz == 0 ? Double.POSITIVE_INFINITY : (dz > 0 ? z + 1 - z0 : z0 - z) * tdz;
        for (int i = 0; i < steps; i++) {
            double enter;
            if (tx < ty && tx < tz) { enter = tx; x += sx; tx += tdx; }
            else if (ty < tz) { enter = ty; y += sy; ty += tdy; }
            else { enter = tz; z += sz; tz += tdz; }
            if (!grid.opaque(x, y, z)) continue;
            // The depth along the run through the cell is a minimum of linear pieces: it peaks at an end, where the run
            // crosses a centre plane, or (rarely, then we under-count) where two faces are equally near.
            double leave = Math.min(1, Math.min(tx, Math.min(ty, tz)));
            if (deepAt(grid, x, y, z, x0, y0, z0, dx, dy, dz, enter) || deepAt(grid, x, y, z, x0, y0, z0, dx, dy, dz, leave)
                || deepAt(grid, x, y, z, x0, y0, z0, dx, dy, dz, (enter + leave) / 2)) return true;
            double cx = dx == 0 ? -1 : (x + 0.5 - x0) / dx, cy = dy == 0 ? -1 : (y + 0.5 - y0) / dy, cz = dz == 0 ? -1 : (z + 0.5 - z0) / dz;
            if (cx > enter && cx < leave && deepAt(grid, x, y, z, x0, y0, z0, dx, dy, dz, cx)
                || cy > enter && cy < leave && deepAt(grid, x, y, z, x0, y0, z0, dx, dy, dz, cy)
                || cz > enter && cz < leave && deepAt(grid, x, y, z, x0, y0, z0, dx, dy, dz, cz)) return true;
        }
        return false;
    }

    private static boolean deepAt(Grid grid, int x, int y, int z, double x0, double y0, double z0, double dx, double dy, double dz, double t) {
        return deep(grid, x, y, z, x0 + dx * t - x, y0 + dy * t - y, z0 + dz * t - z);
    }

    /** Whether (fx, fy, fz) inside opaque cell (x, y, z) is at least DEPTH from every face it shares with an open cell. */
    private static boolean deep(Grid grid, int x, int y, int z, double fx, double fy, double fz) {
        return !(fx < DEPTH && !grid.opaque(x - 1, y, z) || fx > 1 - DEPTH && !grid.opaque(x + 1, y, z)
            || fy < DEPTH && !grid.opaque(x, y - 1, z) || fy > 1 - DEPTH && !grid.opaque(x, y + 1, z)
            || fz < DEPTH && !grid.opaque(x, y, z - 1) || fz > 1 - DEPTH && !grid.opaque(x, y, z + 1));
    }

    /**
     * Where the camera is, relative to the origin a column-major modelview matrix {@code m} draws from: the point it maps to
     * (0, 0, 0). False when the matrix cannot be inverted.
     */
    public static boolean cameraOf(float[] m, double[] out) {
        double a = m[0], b = m[4], c = m[8], d = m[1], e = m[5], f = m[9], g = m[2], h = m[6], k = m[10];
        double ei = e * k - f * h, fg = f * g - d * k, dh = d * h - e * g, det = a * ei + b * fg + c * dh;
        if (!(Math.abs(det) > 1e-9)) return false;
        double tx = -m[12], ty = -m[13], tz = -m[14];
        out[0] = (ei * tx + (c * h - b * k) * ty + (b * f - c * e) * tz) / det;
        out[1] = (fg * tx + (a * k - c * g) * ty + (c * d - a * f) * tz) / det;
        out[2] = (dh * tx + (b * g - a * h) * ty + (a * e - b * d) * tz) / det;
        return true;
    }

    private static int count(double size) { return Math.max(2, (int) Math.ceil(size / STEP) + 1); }
    private static int floor(double v) { int i = (int) v; return v < i ? i - 1 : i; }
    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : v > hi ? hi : v; }
}
