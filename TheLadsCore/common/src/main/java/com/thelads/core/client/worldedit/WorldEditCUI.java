package com.thelads.core.client.worldedit;

import com.thelads.core.client.ChunkBorders.Lines;
import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import java.nio.charset.StandardCharsets;

/**
 * WorldEdit CUI Selection Renderer (Lunar / Badlion WorldEdit CUI):
 * Listens on the 'worldedit:cui' and 'WECUI' plugin channels to visualize WorldEdit cuboid selections
 * as 3D bounding box outlines, block highlights, and face grids in world space.
 * Protocol: WorldEdit CUI protocol version 4.
 * GATED: risky set in HypixelSafetyManager, default OFF.
 */
public final class WorldEditCUI {
    public static final String NAME = "WorldEdit CUI";
    public static final String CHANNEL_MODERN = "worldedit:cui";
    public static final String CHANNEL_LEGACY = "WECUI";

    public static final String COLOR_POS1 = "Pos 1 Color";
    public static final String COLOR_POS2 = "Pos 2 Color";
    public static final String COLOR_GRID = "Grid Color";
    public static final String SHOW_GRID = "Show Grid";
    public static final String GRID_SPACING = "Grid Spacing";

    public static boolean on = false;
    public static int colorPos1 = 0xCCFF3333; // Red
    public static int colorPos2 = 0xCC3333FF; // Blue
    public static int colorGrid = 0x8033FF33; // Green
    public static boolean showGrid = true;
    public static int spacing = 1;

    public record BlockPos(int x, int y, int z) {}

    public enum Shape {
        NONE, CUBOID, UNSUPPORTED
    }

    private static Shape shape = Shape.NONE;
    private static BlockPos pos1 = null;
    private static BlockPos pos2 = null;
    private static Module module;

    private WorldEditCUI() {}

    public static Module createModule() {
        // GATED: Default OFF
        Module m = new Module(NAME, "Visualizes WorldEdit cuboid selections with 3D grid lines.");
        m.setEnabled(false);
        m.addOption(new ColorOption(COLOR_POS1, false, 0xCCFF3333));
        m.addOption(new ColorOption(COLOR_POS2, false, 0xCC3333FF));
        m.addOption(new ColorOption(COLOR_GRID, false, 0x8033FF33));
        m.addOption(new BoolOption(SHOW_GRID, true));
        m.addOption(new SliderOption(GRID_SPACING, 1, 1, 5, 1));
        return m;
    }

    public static boolean hasSelection() {
        return shape == Shape.CUBOID && pos1 != null;
    }

    public static void clear() {
        shape = Shape.NONE;
        pos1 = null;
        pos2 = null;
    }

    /** Called once per client tick. */
    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        boolean enabled = module != null && module.isEnabled();
        // Hypixel safety check
        if (HypixelSafetyManager.isDisallowed(NAME)) {
            on = false;
            return;
        }
        on = enabled;
        if (!on) return;

        colorPos1 = color(COLOR_POS1, colorPos1);
        colorPos2 = color(COLOR_POS2, colorPos2);
        colorGrid = color(COLOR_GRID, colorGrid);
        showGrid = !(module.getOption(SHOW_GRID) instanceof BoolOption b) || b.get();
        spacing = module.getOption(GRID_SPACING) instanceof SliderOption s ? Math.max(1, (int) s.getValue()) : 1;
    }

    private static int color(String name, int fallback) {
        return module != null && module.getOption(name) instanceof ColorOption c ? c.getColor() : fallback;
    }

    /** Handles incoming packet on 'worldedit:cui' or 'WECUI'. */
    public static void onPacket(String channel, byte[] data) {
        if (data == null || data.length == 0) return;
        if (!CHANNEL_MODERN.equalsIgnoreCase(channel) && !CHANNEL_LEGACY.equalsIgnoreCase(channel)) return;

        String message = new String(data, StandardCharsets.UTF_8);
        String[] lines = message.split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            parseCommand(line);
        }
    }

    /** Parses a single WorldEdit CUI command line. Package-private for unit testing. */
    public static void parseCommand(String line) {
        String[] parts = line.split("\\|");
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();
        switch (cmd) {
            case "s": // Shape: s|cuboid - per CUI protocol, s|<shape> clears current selection first
                clear();
                if (parts.length >= 2) {
                    String shapeType = parts[1].toLowerCase();
                    if (shapeType.startsWith("cuboid")) {
                        shape = Shape.CUBOID;
                    } else {
                        shape = Shape.UNSUPPORTED;
                    }
                } else {
                    shape = Shape.UNSUPPORTED;
                }
                break;
            case "p": // Point: p|<id>|<x>|<y>|<z>|<size>
                if (parts.length >= 5) {
                    try {
                        int id = Integer.parseInt(parts[1]);
                        int x = Integer.parseInt(parts[2]);
                        int y = Integer.parseInt(parts[3]);
                        int z = Integer.parseInt(parts[4]);
                        BlockPos pos = new BlockPos(x, y, z);
                        if (id == 0) {
                            pos1 = pos;
                            if (shape == Shape.NONE) shape = Shape.CUBOID;
                        } else if (id == 1) {
                            pos2 = pos;
                            if (shape == Shape.NONE) shape = Shape.CUBOID;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                break;
        }
    }

    /** Returns the CUI handshake packet payload (v|4). */
    public static byte[] getHandshakePayload() {
        return "v|4".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Renders 3D selection grid lines in the world render pass.
     */
    public static void draw(Lines out) {
        if (!on || !hasSelection() || out == null) return;
        if (HypixelSafetyManager.isDisallowed(NAME)) return;

        // Copy pos1/pos2 to local variables to prevent race condition with clear()
        BlockPos p1 = pos1;
        BlockPos p2 = pos2;

        if (shape == Shape.CUBOID && p1 != null) {
            int x1 = p1.x();
            int y1 = p1.y();
            int z1 = p1.z();
            int x2 = p2 != null ? p2.x() : x1;
            int y2 = p2 != null ? p2.y() : y1;
            int z2 = p2 != null ? p2.z() : z1;

            double minX = Math.min(x1, x2);
            double minY = Math.min(y1, y2);
            double minZ = Math.min(z1, z2);
            double maxX = Math.max(x1, x2) + 1.0;
            double maxY = Math.max(y1, y2) + 1.0;
            double maxZ = Math.max(z1, z2) + 1.0;

            // Draw bounding box wireframe
            drawBoxOutline(out, minX, minY, minZ, maxX, maxY, maxZ, colorGrid);

            // Draw face grids if spacing > 0
            if (showGrid && spacing > 0) {
                drawGrid(out, minX, minY, minZ, maxX, maxY, maxZ, colorGrid, spacing);
            }

            // Draw Pos 1 highlight box (red)
            drawBoxOutline(out, x1, y1, z1, x1 + 1.0, y1 + 1.0, z1 + 1.0, colorPos1);

            // Draw Pos 2 highlight box (blue)
            if (p2 != null) {
                drawBoxOutline(out, x2, y2, z2, x2 + 1.0, y2 + 1.0, z2 + 1.0, colorPos2);
            }
        }
    }

    private static void drawBoxOutline(Lines out, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
        // Bottom 4 edges
        out.line(x0, y0, z0, x1, y0, z0, color);
        out.line(x1, y0, z0, x1, y0, z1, color);
        out.line(x1, y0, z1, x0, y0, z1, color);
        out.line(x0, y0, z1, x0, y0, z0, color);

        // Top 4 edges
        out.line(x0, y1, z0, x1, y1, z0, color);
        out.line(x1, y1, z0, x1, y1, z1, color);
        out.line(x1, y1, z1, x0, y1, z1, color);
        out.line(x0, y1, z1, x0, y1, z0, color);

        // Vertical 4 pillars
        out.line(x0, y0, z0, x0, y1, z0, color);
        out.line(x1, y0, z0, x1, y1, z0, color);
        out.line(x1, y0, z1, x1, y1, z1, color);
        out.line(x0, y0, z1, x0, y1, z1, color);
    }

    private static void drawGrid(Lines out, double x0, double y0, double z0, double x1, double y1, double z1, int color, int step) {
        // X-Z grids along Y
        for (double y = y0 + step; y < y1; y += step) {
            out.line(x0, y, z0, x1, y, z0, color);
            out.line(x1, y, z0, x1, y, z1, color);
            out.line(x1, y, z1, x0, y, z1, color);
            out.line(x0, y, z1, x0, y, z0, color);
        }
        // Vertical lines along X and Z
        for (double x = x0 + step; x < x1; x += step) {
            out.line(x, y0, z0, x, y1, z0, color);
            out.line(x, y0, z1, x, y1, z1, color);
        }
        for (double z = z0 + step; z < z1; z += step) {
            out.line(x0, y0, z, x0, y1, z, color);
            out.line(x1, y0, z, x1, y1, z, color);
        }
    }

    // Accessors for testing
    public static Shape getShape() { return shape; }
    public static BlockPos getPos1() { return pos1; }
    public static BlockPos getPos2() { return pos2; }
}
