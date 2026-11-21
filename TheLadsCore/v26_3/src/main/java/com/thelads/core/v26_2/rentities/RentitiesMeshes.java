package com.thelads.core.v26_2.rentities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

/**
 * Finds the mesh of each entity model: Entity Scan Mode extracts it from the model's cuboids and saves it under
 * config/theladscore/rentities/, normal mode only loads saved meshes (a model with no saved mesh is drawn by vanilla).
 */
final class RentitiesMeshes {
    /** Per Model instance: its parts in render order and its mesh, found once. */
    static final class Entry {
        final ModelPart[] parts;
        final int[] parent;
        final long signature;
        final File file;
        /** Scratch for the per-frame bone chain. */
        final Matrix4f[] world;
        final boolean[] hidden;
        RentitiesMesh mesh;
        boolean loadTried;
        boolean scanned;

        Entry(ModelPart[] parts, int[] parent, long signature, File file) {
            this.parts = parts;
            this.parent = parent;
            this.signature = signature;
            this.file = file;
            this.world = new Matrix4f[parts.length];
            this.hidden = new boolean[parts.length];
            for (int i = 0; i < world.length; i++) world[i] = new Matrix4f();
        }
    }

    private static final Map<Model<?>, Entry> ENTRIES = new WeakHashMap<>();
    private static File directory;
    private static int scanSaved;

    static void directory(File dir) { directory = dir; }

    static File directory() { return directory; }

    static Entry entry(Model<?> model, boolean scan) {
        Entry e = ENTRIES.get(model);
        if (e == null) {
            List<ModelPart> parts = new ArrayList<>();
            List<Integer> parents = new ArrayList<>();
            walk(model.root(), -1, parts, parents);
            int[] parent = new int[parents.size()];
            for (int i = 0; i < parent.length; i++) parent[i] = parents.get(i);
            ModelPart[] array = parts.toArray(new ModelPart[0]);
            long sig = signature(model, array, parent);
            String cls = model.getClass().getSimpleName().replaceAll("[^A-Za-z0-9_]", "_");
            e = new Entry(array, parent, sig, new File(directory, cls + "_" + Long.toHexString(sig) + ".bin"));
            ENTRIES.put(model, e);
        }
        if (scan) {
            if (!e.scanned) {
                e.scanned = true;
                try {
                    extract(e).save(e.file);
                    scanSaved++;
                } catch (Exception ex) {
                    RentitiesRenderer.warn("could not save " + e.file.getName() + ": " + ex);
                }
            }
        } else if (e.mesh == null && !e.loadTried) {
            e.loadTried = true;
            e.mesh = RentitiesMesh.load(e.file, e.signature);
        }
        return e;
    }

    static int scanSaved() { return scanSaved; }

    static int cachedFiles() {
        File[] files = directory == null ? null : directory.listFiles((d, n) -> n.endsWith(".bin"));
        return files == null ? 0 : files.length;
    }

    /** Forget everything in memory (cache deleted or scan finished) so meshes are read from disk again. */
    static void reset() {
        for (Entry e : ENTRIES.values()) if (e.mesh != null) e.mesh.release();
        ENTRIES.clear();
        scanSaved = 0;
    }

    static int deleteFiles() {
        int n = 0;
        File[] files = directory == null ? null : directory.listFiles((d, name) -> name.endsWith(".bin") || name.endsWith(".tmp"));
        if (files != null) for (File f : files) if (f.delete()) n++;
        return n;
    }

    /** Same order as ModelPart.render: the part, then its children in map order. Every part counts, visible or not. */
    private static void walk(ModelPart part, int parent, List<ModelPart> parts, List<Integer> parents) {
        int index = parts.size();
        parts.add(part);
        parents.add(parent);
        for (ModelPart child : part.children.values()) walk(child, index, parts, parents);
    }

    private static long signature(Model<?> model, ModelPart[] parts, int[] parent) {
        long h = 0xcbf29ce484222325L;
        h = mix(h, model.getClass().getName().hashCode());
        h = mix(h, parts.length);
        for (int i = 0; i < parts.length; i++) {
            h = mix(h, parent[i]);
            ModelPart p = parts[i];
            h = mix(h, p.cubes.size());
            for (ModelPart.Cube cube : p.cubes) {
                h = mix(h, cube.polygons.length);
                for (ModelPart.Polygon poly : cube.polygons) {
                    h = mix(h, poly.vertices().length);
                    for (ModelPart.Vertex v : poly.vertices()) {
                        h = mix(h, Float.floatToIntBits(v.worldX()));
                        h = mix(h, Float.floatToIntBits(v.worldY()));
                        h = mix(h, Float.floatToIntBits(v.worldZ()));
                        h = mix(h, Float.floatToIntBits(v.u()));
                        h = mix(h, Float.floatToIntBits(v.v()));
                    }
                }
            }
        }
        return h;
    }

    private static long mix(long h, int v) {
        h ^= v & 0xffffffffL;
        return h * 0x100000001b3L;
    }

    private static RentitiesMesh extract(Entry e) {
        int vertexTotal = 0, indexTotal = 0;
        for (ModelPart p : e.parts) for (ModelPart.Cube c : p.cubes) for (ModelPart.Polygon poly : c.polygons) {
            int n = poly.vertices().length;
            if (n < 3) continue;
            vertexTotal += n;
            indexTotal += (n - 2) * 3;
        }
        float[] data = new float[vertexTotal * 8];
        int[] bones = new int[vertexTotal];
        int[] indices = new int[indexTotal];
        int vi = 0, ii = 0;
        for (int bone = 0; bone < e.parts.length; bone++) {
            for (ModelPart.Cube c : e.parts[bone].cubes) for (ModelPart.Polygon poly : c.polygons) {
                ModelPart.Vertex[] vs = poly.vertices();
                if (vs.length < 3) continue;
                Vector3fc normal = poly.normal();
                int base = vi;
                for (ModelPart.Vertex v : vs) {
                    int o = vi * 8;
                    data[o] = v.worldX(); data[o + 1] = v.worldY(); data[o + 2] = v.worldZ();
                    data[o + 3] = v.u(); data[o + 4] = v.v();
                    data[o + 5] = normal.x(); data[o + 6] = normal.y(); data[o + 7] = normal.z();
                    bones[vi++] = bone;
                }
                for (int k = 1; k < vs.length - 1; k++) {
                    indices[ii++] = base;
                    indices[ii++] = base + k;
                    indices[ii++] = base + k + 1;
                }
            }
        }
        return new RentitiesMesh(e.signature, e.parts.length, data, bones, indices);
    }
}
