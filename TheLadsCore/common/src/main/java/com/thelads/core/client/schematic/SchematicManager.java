package com.thelads.core.client.schematic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Schematic blueprint overlay manager (parity with Badlion Schematica - strictly NO auto-printer mode for anti-ban safety).
 */
public final class SchematicManager {
    public record SchematicBlock(int relX, int relY, int relZ, String blockId) {}

    private static String loadedName = null;
    private static final List<SchematicBlock> BLOCKS = new ArrayList<>();
    private static int originX, originY, originZ;

    private SchematicManager() {}

    public static synchronized void load(String name, List<SchematicBlock> blocks) {
        loadedName = name;
        BLOCKS.clear();
        if (blocks != null) BLOCKS.addAll(blocks);
    }

    public static synchronized void setOrigin(int x, int y, int z) {
        originX = x; originY = y; originZ = z;
    }

    public static synchronized int getOriginX() { return originX; }
    public static synchronized int getOriginY() { return originY; }
    public static synchronized int getOriginZ() { return originZ; }

    public static synchronized List<SchematicBlock> getBlocks() {
        return Collections.unmodifiableList(BLOCKS);
    }

    public static synchronized String getLoadedName() {
        return loadedName;
    }

    public static synchronized boolean isLoaded() {
        return loadedName != null && !BLOCKS.isEmpty();
    }

    public static synchronized void clear() {
        loadedName = null;
        BLOCKS.clear();
    }
}
