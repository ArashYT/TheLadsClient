package com.thelads.core.client.ebe;

import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in "baked block entities": chests, shulker boxes, beds, signs, bells... that vanilla redraws every frame with
 * entity-style renderers are meshed into their chunk section as plain block models instead. A block switches to its
 * vanilla renderer only while it animates (lid opening, bell ringing) and back to the baked model afterwards.
 *
 * <p>This class is the version-independent part: the on/off switches and the per-position state machine that keeps
 * the chunk mesh and the block entity renderer from both drawing (or both skipping) a block during a switch.
 * Adapters call {@link #nextFrame()} once per rendered frame, {@link #setAnimating} from the main thread when a block
 * entity starts/stops animating (rebuilding the section when it returns true), {@link #bake} from chunk-meshing threads
 * and {@link #drawDynamic} from the block entity render path.
 */
public final class BakedBlockEntities {
    /** Mod ids that already bake block entities into the chunk mesh; with any of them installed this feature stands down. */
    static final String[] SIMILAR_MODS = {
        "enhancedblockentities", "ebe", "ebe_forge", "ebeforge", "obe", "betterblockentities", "fastchest"
    };

    private static final long UNBUILT = -1L;
    private static final ConcurrentHashMap<Long, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static volatile int frame;
    private static volatile boolean active;
    private static volatile boolean suspended;
    private static volatile String standDownReason = "not initialised";

    private BakedBlockEntities() {}

    private static final class Entry {
        volatile boolean dynamic;
        /** (frame << 1) | (meshed dynamic ? 1 : 0) of the latest section build that saw this position, or UNBUILT. */
        volatile long built = UNBUILT;
    }

    // ---- switches ------------------------------------------------------------------------------------------------

    /**
     * Decides once, at client start, whether baking runs this session: the JVM kill switch {@code -Dlads.ebe=false}
     * and installed mods that do the same job (seen through Fabric, Forge/Connector and legacy Forge loaders).
     */
    public static boolean init() {
        String reason = null;
        if ("false".equalsIgnoreCase(System.getProperty("lads.ebe", "true"))) reason = "-Dlads.ebe=false";
        for (String id : SIMILAR_MODS) {
            if (reason == null && modLoaded(id)) reason = "mod '" + id + "' is installed";
        }
        active = reason == null;
        standDownReason = reason;
        return active;
    }

    /** True when baking runs this session (see {@link #init()}); every hook is a no-op otherwise. */
    public static boolean active() { return active; }

    public static String standDownReason() { return standDownReason; }

    /** Test hook. */
    static void setActive(boolean on) { active = on; }

    /**
     * QA benchmarks only (no user setting): while suspended every block entity is left out of the mesh and drawn by its
     * vanilla renderer, i.e. vanilla behaviour, without a model reload. The caller re-meshes the level after a change.
     */
    public static void setSuspended(boolean on) { suspended = on; }

    public static boolean suspended() { return suspended; }

    /**
     * Vanilla chests use their Christmas texture from December 24 to 26 (checked when the renderer is created, i.e. at
     * every resource reload). {@code -Dlads.ebe.xmas=true|false} forces it for both the baked and the vanilla renderer.
     */
    public static boolean christmas() {
        String forced = System.getProperty("lads.ebe.xmas");
        if (forced != null) return Boolean.parseBoolean(forced);
        return christmas(Calendar.getInstance());
    }

    static boolean christmas(Calendar calendar) {
        int day = calendar.get(Calendar.DATE);
        return calendar.get(Calendar.MONTH) + 1 == 12 && day >= 24 && day <= 26;
    }

    /** True if {@code -Dlads.ebe.xmas} is set, so the vanilla renderer must follow {@link #christmas()} too. */
    public static boolean christmasForced() { return System.getProperty("lads.ebe.xmas") != null; }

    /** Mod presence through every loader Lads runs on; Forge mods under Sinytra Connector are invisible to FabricLoader. */
    public static boolean modLoaded(String id) {
        try {
            Class<?> loader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = loader.getMethod("getInstance").invoke(null);
            if ((Boolean) loader.getMethod("isModLoaded", String.class).invoke(instance, id)) return true;
        } catch (Throwable ignored) {}
        try {
            Class<?> list = Class.forName("net.minecraftforge.fml.ModList");
            Object instance = list.getMethod("get").invoke(null);
            if (instance != null && (Boolean) list.getMethod("isLoaded", String.class).invoke(instance, id)) return true;
        } catch (Throwable ignored) {}
        try {
            Class<?> legacy = Class.forName("net.minecraftforge.fml.common.Loader");
            if ((Boolean) legacy.getMethod("isModLoaded", String.class).invoke(null, id)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    // ---- state machine -------------------------------------------------------------------------------------------

    /** Once per rendered frame, main thread. */
    public static void nextFrame() { frame++; }

    public static int frame() { return frame; }

    /**
     * Main thread: the block entity at {@code pos} (BlockPos#asLong) started or stopped animating.
     * Returns true when its chunk section must be rebuilt.
     */
    public static boolean setAnimating(long pos, boolean animating) {
        Entry entry = ENTRIES.get(pos);
        if (animating) {
            if (entry != null && entry.dynamic) return false;
            if (entry == null) {
                entry = new Entry();
                ENTRIES.put(pos, entry);
            }
            entry.built = UNBUILT;
            entry.dynamic = true;
            return true;
        }
        if (entry == null || !entry.dynamic) return false;
        entry.built = UNBUILT;
        entry.dynamic = false;
        return true;
    }

    /** True while the block at {@code pos} is (or is becoming) drawn by its vanilla renderer. */
    public static boolean animating(long pos) {
        Entry entry = ENTRIES.get(pos);
        return entry != null && entry.dynamic;
    }

    /** Chunk-meshing thread: true if the section being built should contain the baked model for {@code pos}. */
    public static boolean bake(long pos) {
        if (suspended) return false;
        Entry entry = ENTRIES.get(pos);
        if (entry == null) return true;
        boolean dynamic = entry.dynamic;
        entry.built = ((long) frame << 1) | (dynamic ? 1L : 0L);
        return !dynamic;
    }

    /**
     * Main thread, block entity render path: true if the vanilla renderer must draw {@code pos} this frame.
     * It starts one frame after a section build left the baked model out (uploads land by then) and keeps drawing
     * until two frames after a build put it back, so a switch never shows a gap.
     */
    public static boolean drawDynamic(long pos) {
        if (suspended) return true;
        Entry entry = ENTRIES.get(pos);
        if (entry == null) return false;
        long built = entry.built;
        int builtFrame = (int) (built >> 1);
        boolean builtDynamic = built != UNBUILT && (built & 1L) != 0;
        if (entry.dynamic) return builtDynamic && frame > builtFrame;
        if (built != UNBUILT && !builtDynamic && frame > builtFrame + 1) {
            ENTRIES.remove(pos, entry);
            return false;
        }
        return true;
    }

    /** Main thread: the block entity at {@code pos} is gone. */
    public static void forget(long pos) { ENTRIES.remove(pos); }

    /** World change or resource reload: everything is rebuilt from scratch anyway. */
    public static void clear() { ENTRIES.clear(); }

    static int tracked() { return ENTRIES.size(); }
}
