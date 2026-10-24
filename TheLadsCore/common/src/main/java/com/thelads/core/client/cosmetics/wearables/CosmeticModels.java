package com.thelads.core.client.cosmetics.wearables;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 3D box definitions and precomputed mesh buffers for each wearable hat and glasses cosmetic.
 * All coordinates are relative to the player head origin (0, 0, 0):
 * head cube extends -4 to 4 (X/Z) and -8 to 0 (Y).
 */
public final class CosmeticModels {
    public static final int STRIDE = 8; // x, y, z, u, v, nx, ny, nz

    private static final Map<String, List<CosmeticModelBox>> BOXES = new LinkedHashMap<>();
    private static final Map<String, float[]> MESHES = new LinkedHashMap<>();

    static {
        // Classic Top Hat
        BOXES.put("lads:hat_top", List.of(
            new CosmeticModelBox(-6.0f, -8.5f, -6.0f, 12, 1, 12, 0, 0),      // Brim
            new CosmeticModelBox(-4.0f, -16.5f, -4.0f, 8, 8, 8, 0, 16),     // Crown
            new CosmeticModelBox(-4.2f, -10.5f, -4.2f, 8.4f, 2, 8.4f, 0, 36) // Ribbon
        ));

        // Golden Halo
        BOXES.put("lads:hat_halo", List.of(
            new CosmeticModelBox(-4.5f, -12.0f, -5.0f, 9, 1, 1, 0, 0),
            new CosmeticModelBox(-4.5f, -12.0f, 4.0f, 9, 1, 1, 0, 4),
            new CosmeticModelBox(-5.0f, -12.0f, -4.5f, 1, 1, 9, 0, 8),
            new CosmeticModelBox(4.0f, -12.0f, -4.5f, 1, 1, 9, 0, 20)
        ));

        // Royal Crown
        BOXES.put("lads:hat_crown", List.of(
            new CosmeticModelBox(-4.5f, -9.5f, -4.5f, 9, 2, 9, 0, 0),
            new CosmeticModelBox(-4.5f, -11.5f, -4.5f, 2, 2, 2, 0, 16),
            new CosmeticModelBox(2.5f, -11.5f, -4.5f, 2, 2, 2, 10, 16),
            new CosmeticModelBox(-4.5f, -11.5f, 2.5f, 2, 2, 2, 20, 16),
            new CosmeticModelBox(2.5f, -11.5f, 2.5f, 2, 2, 2, 30, 16)
        ));

        // Cozy Beanie
        BOXES.put("lads:hat_beanie", List.of(
            new CosmeticModelBox(-4.5f, -11.0f, -4.5f, 9, 4, 9, 0, 0),
            new CosmeticModelBox(-4.7f, -8.5f, -4.7f, 9.4f, 2, 9.4f, 0, 16),
            new CosmeticModelBox(-1.5f, -13.0f, -1.5f, 3, 2, 3, 0, 32)
        ));

        // Dark Sunglasses
        BOXES.put("lads:glasses_sunglasses", List.of(
            new CosmeticModelBox(-4.5f, -5.5f, -4.8f, 9, 2.5f, 1, 0, 0),
            new CosmeticModelBox(-4.5f, -5.0f, -4.0f, 0.5f, 1, 5, 0, 10),
            new CosmeticModelBox(4.0f, -5.0f, -4.0f, 0.5f, 1, 5, 12, 10)
        ));

        // Steampunk Goggles
        BOXES.put("lads:glasses_goggles", List.of(
            new CosmeticModelBox(-3.8f, -5.5f, -5.0f, 3, 3, 2, 0, 0),
            new CosmeticModelBox(0.8f, -5.5f, -5.0f, 3, 3, 2, 12, 0),
            new CosmeticModelBox(-0.8f, -4.5f, -4.8f, 1.6f, 1, 1, 24, 0),
            new CosmeticModelBox(-4.6f, -4.5f, -4.6f, 9.2f, 1, 9.2f, 0, 10)
        ));

        // Ninja Mask / Bandana
        BOXES.put("lads:glasses_bandana", List.of(
            new CosmeticModelBox(-4.3f, -4.0f, -4.3f, 8.6f, 4.2f, 8.6f, 0, 0),
            new CosmeticModelBox(-1.0f, -3.0f, 4.4f, 2, 3, 1, 0, 20)
        ));

        // Precompute mesh arrays for zero allocations during rendering
        for (Map.Entry<String, List<CosmeticModelBox>> entry : BOXES.entrySet()) {
            MESHES.put(entry.getKey(), buildMesh(entry.getValue()));
        }
    }

    private CosmeticModels() {}

    public static List<CosmeticModelBox> getBoxes(String itemId) {
        if (itemId == null) return Collections.emptyList();
        List<CosmeticModelBox> list = BOXES.get(itemId);
        return list == null ? Collections.emptyList() : list;
    }

    public static float[] getMesh(String itemId) {
        if (itemId == null) return null;
        return MESHES.get(itemId);
    }

    private static float[] buildMesh(List<CosmeticModelBox> boxes) {
        if (boxes == null || boxes.isEmpty()) return new float[0];
        float[] mesh = new float[boxes.size() * 6 * 4 * STRIDE];
        int offset = 0;
        for (CosmeticModelBox box : boxes) {
            float x0 = box.x() * 0.0625f;
            float x1 = (box.x() + box.w()) * 0.0625f;
            float y0 = box.y() * 0.0625f;
            float y1 = (box.y() + box.h()) * 0.0625f;
            float z0 = box.z() * 0.0625f;
            float z1 = (box.z() + box.d()) * 0.0625f;

            float u = box.u();
            float v = box.v();
            float w = box.w();
            float h = box.h();
            float d = box.d();

            // Face 0: DOWN (bottom face, normal 0, -1, 0)
            float u_d0 = (u + d) / 64f, v_d0 = v / 64f;
            float u_d1 = (u + d + w) / 64f, v_d1 = (v + d) / 64f;
            offset = writeQuad(mesh, offset,
                x1, y0, z1, u_d1, v_d0,
                x0, y0, z1, u_d0, v_d0,
                x0, y0, z0, u_d0, v_d1,
                x1, y0, z0, u_d1, v_d1,
                0, -1, 0);

            // Face 1: UP (top face, normal 0, 1, 0)
            float u_u0 = (u + d + w) / 64f, v_u0 = (v + d) / 64f;
            float u_u1 = (u + d + w + w) / 64f, v_u1 = v / 64f;
            offset = writeQuad(mesh, offset,
                x1, y1, z0, u_u1, v_u0,
                x0, y1, z0, u_u0, v_u0,
                x0, y1, z1, u_u0, v_u1,
                x1, y1, z1, u_u1, v_u1,
                0, 1, 0);

            // Face 2: WEST (left face, normal -1, 0, 0)
            float u_w0 = u / 64f, v_w0 = (v + d) / 64f;
            float u_w1 = (u + d) / 64f, v_w1 = (v + d + h) / 64f;
            offset = writeQuad(mesh, offset,
                x0, y0, z0, u_w1, v_w0,
                x0, y0, z1, u_w0, v_w0,
                x0, y1, z1, u_w0, v_w1,
                x0, y1, z0, u_w1, v_w1,
                -1, 0, 0);

            // Face 3: NORTH (front face, normal 0, 0, -1)
            float u_n0 = (u + d) / 64f, v_n0 = (v + d) / 64f;
            float u_n1 = (u + d + w) / 64f, v_n1 = (v + d + h) / 64f;
            offset = writeQuad(mesh, offset,
                x1, y0, z0, u_n1, v_n0,
                x0, y0, z0, u_n0, v_n0,
                x0, y1, z0, u_n0, v_n1,
                x1, y1, z0, u_n1, v_n1,
                0, 0, -1);

            // Face 4: EAST (right face, normal 1, 0, 0)
            float u_e0 = (u + d + w) / 64f, v_e0 = (v + d) / 64f;
            float u_e1 = (u + d + w + d) / 64f, v_e1 = (v + d + h) / 64f;
            offset = writeQuad(mesh, offset,
                x1, y0, z1, u_e1, v_e0,
                x1, y0, z0, u_e0, v_e0,
                x1, y1, z0, u_e0, v_e1,
                x1, y1, z1, u_e1, v_e1,
                1, 0, 0);

            // Face 5: SOUTH (back face, normal 0, 0, 1)
            float u_s0 = (u + d + w + d) / 64f, v_s0 = (v + d) / 64f;
            float u_s1 = (u + d + w + d + w) / 64f, v_s1 = (v + d + h) / 64f;
            offset = writeQuad(mesh, offset,
                x0, y0, z1, u_s1, v_s0,
                x1, y0, z1, u_s0, v_s0,
                x1, y1, z1, u_s0, v_s1,
                x0, y1, z1, u_s1, v_s1,
                0, 0, 1);
        }
        return mesh;
    }

    private static int writeQuad(float[] mesh, int off,
        float x0, float y0, float z0, float u0, float v0,
        float x1, float y1, float z1, float u1, float v1,
        float x2, float y2, float z2, float u2, float v2,
        float x3, float y3, float z3, float u3, float v3,
        float nx, float ny, float nz) {
        mesh[off++] = x0; mesh[off++] = y0; mesh[off++] = z0; mesh[off++] = u0; mesh[off++] = v0; mesh[off++] = nx; mesh[off++] = ny; mesh[off++] = nz;
        mesh[off++] = x1; mesh[off++] = y1; mesh[off++] = z1; mesh[off++] = u1; mesh[off++] = v1; mesh[off++] = nx; mesh[off++] = ny; mesh[off++] = nz;
        mesh[off++] = x2; mesh[off++] = y2; mesh[off++] = z2; mesh[off++] = u2; mesh[off++] = v2; mesh[off++] = nx; mesh[off++] = ny; mesh[off++] = nz;
        mesh[off++] = x3; mesh[off++] = y3; mesh[off++] = z3; mesh[off++] = u3; mesh[off++] = v3; mesh[off++] = nx; mesh[off++] = ny; mesh[off++] = nz;
        return off;
    }
}
