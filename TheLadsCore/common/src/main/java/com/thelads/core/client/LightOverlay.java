package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Light Overlay (Lunar/Badlion Light Overlay, Feather Light Level Overlay): an X on every block top around you where hostile mobs
 * can spawn, red where they spawn at any time (dark enough even in daylight) and yellow where only at night (the sky lights it by
 * day). It shows what vanilla does not, so it is a risky module (HypixelSafetyManager), off by default.
 *
 * <p>The versions scan a few columns of the area every client tick ({@link #columnsThisTick}, so the whole area takes
 * {@link #SWEEP_TICKS} ticks and no tick pays for all of it) and keep up to {@link #PER_COLUMN} markers per column here, in
 * absolute block coordinates; their world-render hook draws {@link #count} markers per column. Both run on the client thread.
 */
public final class LightOverlay {
    public static final String NAME = "Light Overlay", RANGE = "Range", NIGHT = "Night spots", SPAWN_COLOR = "Spawn color",
        NIGHT_COLOR = "Night color";
    public static final int MAX_RADIUS = 24, PER_COLUMN = 4, SWEEP_TICKS = 8;
    /** Markers: none, spawnable only at night (yellow), spawnable at any time (red). */
    public static final int NONE = 0, NIGHT_ONLY = 1, ALWAYS = 2;
    private static final int SIDE = 2 * MAX_RADIUS + 1;
    /** Per column (index {@code dz * side + dx} of the current radius): its markers' x, y, z and kind. */
    public static final int[] X = new int[SIDE * SIDE * PER_COLUMN], Y = new int[X.length], Z = new int[X.length];
    public static final byte[] KIND = new byte[X.length];
    private static final byte[] COUNT = new byte[SIDE * SIDE];
    private static boolean active, night;
    private static int radius = 16, cursor, spawnColor, nightColor;

    private LightOverlay() {}

    public static Module create() {
        Module module = new Module(NAME, "Marks every block top around you where hostile mobs can spawn: red at any time, yellow only at night.");
        module.addOption(new SliderOption(RANGE, 16, 8, MAX_RADIUS, 1));
        module.addOption(new BoolOption(NIGHT, true));
        module.addOption(new ColorOption(SPAWN_COLOR, false, 0xFFFF3030));
        module.addOption(new ColorOption(NIGHT_COLOR, false, 0xFFFFD020));
        return module;
    }

    /** Reads the settings into fields (each version's client tick), so neither the scan nor the draw looks anything up. */
    public static void refresh() {
        Module module = ModuleManager.getInstance().getModule(NAME);
        boolean on = module != null && module.isEnabled();
        int range = on && module.getOption(RANGE) instanceof SliderOption s ? s.getIntValue() : radius;
        if (on != active || range != radius) clear(); // switched, or a new area: rescan from scratch
        active = on;
        radius = Math.max(1, Math.min(MAX_RADIUS, range));
        if (!on) return;
        night = !(module.getOption(NIGHT) instanceof BoolOption b) || b.get();
        spawnColor = color(module, SPAWN_COLOR, 0xFFFF3030);
        nightColor = color(module, NIGHT_COLOR, 0xFFFFD020);
    }

    private static int color(Module module, String name, int fallback) {
        return module.getOption(name) instanceof ColorOption c ? c.getColor() | 0xFF000000 : fallback;
    }

    public static boolean active() { return active; }
    public static int radius() { return radius; }
    /** Columns of the area: (2 x radius + 1) squared. */
    public static int columns() { int side = 2 * radius + 1; return side * side; }

    /** How many columns this tick rescans: the whole area once every {@link #SWEEP_TICKS} ticks. */
    public static int columnsThisTick() { return (columns() + SWEEP_TICKS - 1) / SWEEP_TICKS; }

    /** The next column to rescan (its markers are cleared): x offset {@code column % side - radius}, z offset {@code column / side - radius}. */
    public static int nextColumn() {
        int column = cursor++ % columns();
        if (cursor >= columns()) cursor = 0;
        COUNT[column] = 0;
        return column;
    }

    /** Adds a marker on top of block (x, y - 1, z), i.e. at the air block (x, y, z) a mob would stand in; extra ones are dropped. */
    public static void add(int column, int x, int y, int z, int kind) {
        int n = COUNT[column];
        if (kind == NONE || n >= PER_COLUMN || kind == NIGHT_ONLY && !night) return;
        int slot = column * PER_COLUMN + n;
        X[slot] = x; Y[slot] = y; Z[slot] = z; KIND[slot] = (byte) kind;
        COUNT[column] = (byte) (n + 1);
    }

    public static int count(int column) { return COUNT[column]; }

    public static int color(int kind) { return kind == ALWAYS ? spawnColor : nightColor; }

    public static void clear() { java.util.Arrays.fill(COUNT, (byte) 0); cursor = 0; }

    /**
     * The marker for a block a mob could stand in, from its block light and sky light: mobs spawn where the block light is at most
     * {@code blockLimit} (7 before 1.18, the dimension's limit after: 0 in the Overworld) and the light from the sky is at most 7, which
     * the sky gives at night; a spot with more sky light spawns mobs only at night.
     */
    public static int kind(int blockLight, int skyLight, int blockLimit) {
        if (blockLight > blockLimit) return NONE;
        return skyLight <= 7 ? ALWAYS : NIGHT_ONLY;
    }
}
