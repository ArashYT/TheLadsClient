package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Chunk Borders (Lunar's and Badlion's Chunk Borders): F3+G's lines in your colours, on every version (1.8.9 has no F3+G borders):
 * the corners of the chunk you stand in and of the chunks around it, from the bottom of the world to the top, and on the walls of
 * your chunk a grid every Grid Spacing blocks with a line at every 16-block section boundary. Each version draws {@link #draw}'s lines
 * after the world. Shows what F3+G shows: visual only.
 */
public final class ChunkBorders {
    public static final String NAME = "Chunk Borders", CORNER = "Chunk Corners", NEIGHBOR = "Nearby Chunk Corners", GRID = "Grid",
        GRID_COLOR = "Grid Color", SPACING = "Grid Spacing", SECTION = "Section Lines";

    /** Where a version draws a line (world coordinates, ARGB). */
    public interface Lines {
        void line(double x0, double y0, double z0, double x1, double y1, double z1, int argb);
    }

    public static boolean on;
    static int corner = 0xFFFFFF00, neighbor = 0xFFFF0000, grid = 0x80FFFF00, section = 0xFF4040FF, spacing = 2;
    static boolean showGrid = true;
    private static Module module;

    private ChunkBorders() {}

    /** Once a client tick. */
    public static void tick() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        on = module != null && module.isEnabled();
        if (!on) return;
        corner = color(CORNER, corner);
        neighbor = color(NEIGHBOR, neighbor);
        grid = color(GRID_COLOR, grid);
        section = color(SECTION, section);
        showGrid = !(module.getOption(GRID) instanceof BoolOption b) || b.get();
        spacing = module.getOption(SPACING) instanceof SliderOption s ? Math.max(1, (int) s.getValue()) : 2;
    }

    /** The lines for chunk ({@code chunkX}, {@code chunkZ}) from {@code minY} to {@code maxY}; transparent colours are left out. */
    public static void draw(int chunkX, int chunkZ, int minY, int maxY, Lines out) {
        int x0 = chunkX << 4, z0 = chunkZ << 4, x1 = x0 + 16, z1 = z0 + 16;
        for (int dx = -16; dx <= 32; dx += 16) {
            for (int dz = -16; dz <= 32; dz += 16) {
                boolean own = (dx == 0 || dx == 16) && (dz == 0 || dz == 16);
                vertical(out, x0 + dx, z0 + dz, minY, maxY, own ? corner : neighbor);
            }
        }
        if (showGrid && grid >>> 24 != 0) {
            for (int i = spacing; i < 16; i += spacing) {
                vertical(out, x0 + i, z0, minY, maxY, grid);
                vertical(out, x0 + i, z1, minY, maxY, grid);
                vertical(out, x0, z0 + i, minY, maxY, grid);
                vertical(out, x1, z0 + i, minY, maxY, grid);
            }
            for (int y = Math.floorDiv(minY + spacing - 1, spacing) * spacing; y <= maxY; y += spacing) {
                if (Math.floorMod(y, 16) != 0) ring(out, x0, z0, x1, z1, y, grid);
            }
        }
        if (section >>> 24 != 0) for (int y = Math.floorDiv(minY + 15, 16) * 16; y <= maxY; y += 16) ring(out, x0, z0, x1, z1, y, section);
    }

    private static void vertical(Lines out, int x, int z, int minY, int maxY, int argb) {
        if (argb >>> 24 != 0) out.line(x, minY, z, x, maxY, z, argb);
    }

    private static void ring(Lines out, int x0, int z0, int x1, int z1, int y, int argb) {
        out.line(x0, y, z0, x1, y, z0, argb);
        out.line(x1, y, z0, x1, y, z1, argb);
        out.line(x1, y, z1, x0, y, z1, argb);
        out.line(x0, y, z1, x0, y, z0, argb);
    }

    private static int color(String name, int fallback) {
        return module.getOption(name) instanceof ColorOption option ? PvpVisuals.argb(option) : fallback;
    }
}
