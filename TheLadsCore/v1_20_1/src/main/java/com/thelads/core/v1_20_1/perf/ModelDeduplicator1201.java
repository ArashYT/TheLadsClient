package com.thelads.core.v1_20_1.perf;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Deduplication cache for model quads and vertex data in Minecraft 1.20.1.
 * Canonicalizes 32-element integer vertex arrays and BakedQuad instances to save heap memory
 * and reduce GC pressure across large modpacks.
 *
 * <p>Uses ConcurrentHashMap with strong keys and WeakReference values backed by a 4096-slot
 * ring buffer to ensure entries survive minor GC during model baking without memory leaks.
 */
public final class ModelDeduplicator1201 {
    private static final int RECENT_RING_SIZE = 4096;
    private static final int RECENT_RING_MASK = RECENT_RING_SIZE - 1;
    private static final int PRUNE_THRESHOLD = 16384;

    private static final Map<VertexKey, WeakReference<int[]>> VERTEX_CACHE = new ConcurrentHashMap<>();
    private static final Map<QuadKey, WeakReference<BakedQuad>> QUAD_CACHE = new ConcurrentHashMap<>();

    private static final BakedQuad[] RECENT_QUADS = new BakedQuad[RECENT_RING_SIZE];
    private static int recentQuadIndex = 0;

    private static final int[][] RECENT_VERTICES = new int[RECENT_RING_SIZE][];
    private static int recentVertexIndex = 0;

    private ModelDeduplicator1201() {}

    /**
     * Canonicalizes vertex integer arrays to eliminate identical duplicate buffers.
     */
    public static synchronized int[] canonicalizeVertices(int[] vertices) {
        if (vertices == null || vertices.length == 0) return vertices;
        VertexKey key = new VertexKey(vertices);
        WeakReference<int[]> ref = VERTEX_CACHE.get(key);
        int[] existing = ref != null ? ref.get() : null;
        if (existing != null) {
            return existing;
        }

        VERTEX_CACHE.put(key, new WeakReference<>(vertices));
        RECENT_VERTICES[recentVertexIndex++ & RECENT_RING_MASK] = vertices;

        if (VERTEX_CACHE.size() > PRUNE_THRESHOLD) {
            pruneStaleVertices();
        }
        return vertices;
    }

    /**
     * Canonicalizes BakedQuad instances to share duplicate identical quads across models.
     * Also repoints vertices to canonical vertex arrays so quads with different sprites
     * share identical geometry buffers.
     */
    public static synchronized BakedQuad canonicalizeQuad(BakedQuad quad) {
        if (quad == null) return null;

        // 1. Canonicalize the 32-element integer vertex array
        int[] canonicalVerts = canonicalizeVertices(quad.getVertices());

        // 2. Build the QuadKey using the canonical vertex array, sprite, direction, tintIndex, and shade
        QuadKey key = new QuadKey(
            canonicalVerts,
            quad.getSprite(),
            quad.getDirection(),
            quad.getTintIndex(),
            quad.isShade()
        );

        // 3. Lookup in cache
        WeakReference<BakedQuad> ref = QUAD_CACHE.get(key);
        BakedQuad existing = ref != null ? ref.get() : null;
        if (existing != null) {
            return existing;
        }

        // 4. Construct a new BakedQuad with the canonical vertex array if vertices were deduplicated
        BakedQuad canonicalQuad = quad;
        if (canonicalVerts != quad.getVertices()) {
            canonicalQuad = new BakedQuad(
                canonicalVerts,
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade()
            );
        }

        // 5. Cache the quad with strong ring buffer protection
        QUAD_CACHE.put(key, new WeakReference<>(canonicalQuad));
        RECENT_QUADS[recentQuadIndex++ & RECENT_RING_MASK] = canonicalQuad;

        if (QUAD_CACHE.size() > PRUNE_THRESHOLD) {
            pruneStaleQuads();
        }
        return canonicalQuad;
    }

    /**
     * Canonicalizes quad lists, replacing empty lists with the singleton empty list.
     */
    public static List<BakedQuad> canonicalizeList(List<BakedQuad> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        return list;
    }

    /**
     * Clears all deduplication caches and ring buffers.
     */
    public static synchronized void clear() {
        VERTEX_CACHE.clear();
        QUAD_CACHE.clear();
        Arrays.fill(RECENT_QUADS, null);
        Arrays.fill(RECENT_VERTICES, null);
        recentQuadIndex = 0;
        recentVertexIndex = 0;
    }

    private static void pruneStaleQuads() {
        QUAD_CACHE.entrySet().removeIf(e -> e.getValue().get() == null);
    }

    private static void pruneStaleVertices() {
        VERTEX_CACHE.entrySet().removeIf(e -> e.getValue().get() == null);
    }

    public static int getCachedQuadCount() {
        return QUAD_CACHE.size();
    }

    public static int getCachedVertexCount() {
        return VERTEX_CACHE.size();
    }

    private static final class VertexKey {
        final int[] vertices;
        final int hash;

        VertexKey(int[] vertices) {
            this.vertices = vertices;
            this.hash = Arrays.hashCode(vertices);
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VertexKey other)) return false;
            if (this.vertices == other.vertices) return true;
            return Arrays.equals(this.vertices, other.vertices);
        }
    }

    private static final class QuadKey {
        final int[] vertices;
        final TextureAtlasSprite sprite;
        final Direction direction;
        final int tintIndex;
        final boolean shade;
        final int hash;

        QuadKey(int[] vertices, TextureAtlasSprite sprite, Direction direction, int tintIndex, boolean shade) {
            this.vertices = vertices;
            this.sprite = sprite;
            this.direction = direction;
            this.tintIndex = tintIndex;
            this.shade = shade;
            int h = Arrays.hashCode(vertices);
            h = 31 * h + (sprite != null ? sprite.hashCode() : 0);
            h = 31 * h + (direction != null ? direction.hashCode() : 0);
            h = 31 * h + tintIndex;
            h = 31 * h + (shade ? 1231 : 1237);
            this.hash = h;
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QuadKey other)) return false;
            if (this.tintIndex != other.tintIndex || this.shade != other.shade) return false;
            if (this.direction != other.direction) return false;
            if (this.sprite != other.sprite) return false;
            if (this.vertices == other.vertices) return true;
            return Arrays.equals(this.vertices, other.vertices);
        }
    }
}
