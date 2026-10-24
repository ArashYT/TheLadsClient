package com.thelads.core.client.schematic;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Schematic blueprint overlay manager (parity with Badlion Schematica - strictly read-only ghost blueprint visualizer,
 * NO automated placement / printer mechanics for anti-ban safety).
 * Gated by HypixelSafetyManager (default OFF).
 */
public final class SchematicManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Schematic");
    public static final String NAME = "Schematic Visualizer";
    public static final String TINT_OPTION = "Ghost Tint";
    public static final String DISTANCE_OPTION = "Render Distance";
    public static final String BOUNDING_BOX_ONLY = "Bounding Box Only";

    public interface Lines {
        void line(double x0, double y0, double z0, double x1, double y1, double z1, int argb);
    }

    private static String loadedName = null;
    private static int width, height, length;
    private static final List<SchematicBlock> BLOCKS = new ArrayList<>();
    private static int originX, originY = 64, originZ;
    private static Module module;

    private SchematicManager() {}

    public static boolean isEnabled() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        if (module == null || !module.isEnabled()) return false;
        return !HypixelSafetyManager.isDisallowed(NAME);
    }

    public static synchronized void load(String name, int w, int h, int l, List<SchematicBlock> blocks) {
        loadedName = name;
        width = w;
        height = h;
        length = l;
        BLOCKS.clear();
        if (blocks != null) {
            BLOCKS.addAll(blocks);
        }
        LOGGER.info("Loaded schematic '{}' ({}x{}x{}, {} blocks)", name, w, h, l, BLOCKS.size());
    }

    public static synchronized void setOrigin(int x, int y, int z) {
        originX = x;
        originY = y;
        originZ = z;
    }

    public static synchronized int getOriginX() { return originX; }
    public static synchronized int getOriginY() { return originY; }
    public static synchronized int getOriginZ() { return originZ; }
    public static synchronized int getWidth() { return width; }
    public static synchronized int getHeight() { return height; }
    public static synchronized int getLength() { return length; }
    public static synchronized String getLoadedName() { return loadedName; }
    public static synchronized boolean isLoaded() { return loadedName != null && !BLOCKS.isEmpty(); }
    public static synchronized List<SchematicBlock> getBlocks() { return Collections.unmodifiableList(BLOCKS); }

    public static synchronized void clear() {
        loadedName = null;
        width = height = length = 0;
        BLOCKS.clear();
    }

    /**
     * Loads a classic .schematic file (GZIP NBT format) from a file or stream.
     */
    public static synchronized boolean loadSchematicFile(File file) {
        if (file == null || !file.exists() || !file.canRead()) return false;
        try (InputStream in = new FileInputStream(file)) {
            return loadFromStream(file.getName(), in);
        } catch (Exception e) {
            LOGGER.error("Failed to load schematic file {}: {}", file.getName(), e.getMessage());
            return false;
        }
    }

    public static synchronized boolean loadFromBytes(String name, byte[] data) {
        if (data == null || data.length == 0) return false;
        try (InputStream in = new ByteArrayInputStream(data)) {
            return loadFromStream(name, in);
        } catch (Exception e) {
            LOGGER.error("Failed to parse schematic bytes for {}: {}", name, e.getMessage());
            return false;
        }
    }

    private static boolean loadFromStream(String name, InputStream in) throws IOException {
        InputStream stream = in;
        // Check for gzip header (0x1f, 0x8b)
        in.mark(2);
        int b1 = in.read(), b2 = in.read();
        in.reset();
        if (b1 == 0x1f && b2 == 0x8b) {
            stream = new GZIPInputStream(in);
        }

        DataInputStream dis = new DataInputStream(stream);
        int rootTagType = dis.readByte();
        if (rootTagType != 10) { // Tag Compound
            return false;
        }
        dis.readUTF(); // Root tag name

        short w = 0, h = 0, l = 0;
        byte[] blocks = null;
        byte[] blockData = null;

        while (true) {
            int tagType = dis.readByte();
            if (tagType == 0) break; // Tag_End
            String tagName = dis.readUTF();

            if (tagType == 2) { // Tag_Short
                short val = dis.readShort();
                if ("Width".equalsIgnoreCase(tagName)) w = val;
                else if ("Height".equalsIgnoreCase(tagName)) h = val;
                else if ("Length".equalsIgnoreCase(tagName)) l = val;
            } else if (tagType == 7) { // Tag_ByteArray
                int len = dis.readInt();
                byte[] arr = new byte[len];
                dis.readFully(arr);
                if ("Blocks".equalsIgnoreCase(tagName)) blocks = arr;
                else if ("Data".equalsIgnoreCase(tagName)) blockData = arr;
            } else {
                skipTag(dis, tagType);
            }
        }

        if (w <= 0 || h <= 0 || l <= 0 || blocks == null) {
            return false;
        }

        List<SchematicBlock> list = new ArrayList<>();
        int defaultColor = 0x8055FFFF; // Cyan tint
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int index = (y * l + z) * w + x;
                    if (index < blocks.length) {
                        int id = blocks[index] & 0xFF;
                        if (id > 0) { // Non-air block
                            list.add(new SchematicBlock(x, y, z, id, "block_" + id, defaultColor));
                        }
                    }
                }
            }
        }

        load(name, w, h, l, list);
        return true;
    }

    private static void skipTag(DataInputStream dis, int tagType) throws IOException {
        switch (tagType) {
            case 1 -> dis.skipBytes(1); // Byte
            case 2 -> dis.skipBytes(2); // Short
            case 3 -> dis.skipBytes(4); // Int
            case 4 -> dis.skipBytes(8); // Long
            case 5 -> dis.skipBytes(4); // Float
            case 6 -> dis.skipBytes(8); // Double
            case 7 -> { // Byte_Array
                int len = dis.readInt();
                dis.skipBytes(len);
            }
            case 8 -> dis.readUTF(); // String
            case 9 -> { // List
                int elemType = dis.readByte();
                int count = dis.readInt();
                for (int i = 0; i < count; i++) skipPayload(dis, elemType);
            }
            case 10 -> { // Compound
                while (true) {
                    int t = dis.readByte();
                    if (t == 0) break;
                    dis.readUTF();
                    skipTag(dis, t);
                }
            }
            case 11 -> { // Int_Array
                int len = dis.readInt();
                dis.skipBytes(len * 4);
            }
            case 12 -> { // Long_Array
                int len = dis.readInt();
                dis.skipBytes(len * 8);
            }
            default -> {}
        }
    }

    private static void skipPayload(DataInputStream dis, int tagType) throws IOException {
        switch (tagType) {
            case 1 -> dis.skipBytes(1);
            case 2 -> dis.skipBytes(2);
            case 3 -> dis.skipBytes(4);
            case 4 -> dis.skipBytes(8);
            case 5 -> dis.skipBytes(4);
            case 6 -> dis.skipBytes(8);
            case 7 -> dis.skipBytes(dis.readInt());
            case 8 -> dis.readUTF();
            case 9 -> {
                int elemType = dis.readByte();
                int count = dis.readInt();
                for (int i = 0; i < count; i++) skipPayload(dis, elemType);
            }
            case 10 -> {
                while (true) {
                    int t = dis.readByte();
                    if (t == 0) break;
                    dis.readUTF();
                    skipTag(dis, t);
                }
            }
            case 11 -> dis.skipBytes(dis.readInt() * 4);
            case 12 -> dis.skipBytes(dis.readInt() * 8);
            default -> {}
        }
    }

    /**
     * Renders the ghost blueprint overlay lines into the provided Lines consumer.
     */
    public static synchronized void draw(Lines out, double camX, double camY, double camZ) {
        if (!isEnabled() || !isLoaded() || out == null) return;

        int tintColor = 0x8055FFFF;
        double maxDist = 32.0;
        boolean boxOnly = false;

        if (module != null) {
            if (module.getOption(TINT_OPTION) instanceof ColorOption c) tintColor = c.getColor();
            if (module.getOption(DISTANCE_OPTION) instanceof SliderOption s) maxDist = s.getValue();
            if (module.getOption(BOUNDING_BOX_ONLY) instanceof com.thelads.core.config.BoolOption b) boxOnly = b.get();
        }

        double maxDistSq = maxDist * maxDist;

        // Draw overall bounding box outline in prominent yellow/cyan
        int boxColor = 0xFFFFFF00;
        box(out, originX, originY, originZ, originX + width, originY + height, originZ + length, boxColor);

        if (boxOnly) return;

        // Draw individual ghost blocks
        for (int i = 0; i < BLOCKS.size(); i++) {
            SchematicBlock block = BLOCKS.get(i);
            double bx = originX + block.relX();
            double by = originY + block.relY();
            double bz = originZ + block.relZ();

            double dx = (bx + 0.5) - camX;
            double dy = (by + 0.5) - camY;
            double dz = (bz + 0.5) - camZ;
            if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;

            int color = tintColor != 0 ? tintColor : block.colorArgb();
            box(out, bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0, color);
        }
    }

    /**
     * Emits 12 line segments defining the bounding box wireframe cuboid.
     */
    public static void box(Lines out, double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        if (argb >>> 24 == 0) return;
        // Bottom 4 edges
        out.line(x0, y0, z0, x1, y0, z0, argb);
        out.line(x1, y0, z0, x1, y0, z1, argb);
        out.line(x1, y0, z1, x0, y0, z1, argb);
        out.line(x0, y0, z1, x0, y0, z0, argb);

        // Top 4 edges
        out.line(x0, y1, z0, x1, y1, z0, argb);
        out.line(x1, y1, z0, x1, y1, z1, argb);
        out.line(x1, y1, z1, x0, y1, z1, argb);
        out.line(x0, y1, z1, x0, y1, z0, argb);

        // Vertical 4 edges
        out.line(x0, y0, z0, x0, y1, z0, argb);
        out.line(x1, y0, z0, x1, y1, z0, argb);
        out.line(x1, y0, z1, x1, y1, z1, argb);
        out.line(x0, y0, z1, x0, y1, z1, argb);
    }
}
