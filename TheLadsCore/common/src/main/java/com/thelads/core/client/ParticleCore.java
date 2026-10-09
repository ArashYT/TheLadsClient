package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ParticleCoreModule;
import java.util.Set;

/**
 * Particle Core's decisions, shared by every game version: whether a particle may spawn (per-type rate, potion swirls, the
 * decorative quota), the per-frame settings the render hooks read, and the view frustum. The versions only supply what the game
 * knows (the particle's id, who it belongs to, how far it is) and act on the answer.
 */
public final class ParticleCore {
    public static final String MODULE = "Particle Core";
    public static final String CULLING = "Culling", OPTIMIZED = "Optimized rendering", HIDE_OWN = "Hide own potion particles",
        HIDE_OTHERS = "Hide others' potion particles", RATES = "Spawn rates", LIMIT = "Limit decorative particles",
        PER_TICK = "Decorative particles per tick", DISTANCE = "Decorative particle distance";
    /** Particles that only decorate (smoke, ash, falling leaves, spores, motes): the only ones the optional quota limits. */
    private static final Set<String> DECORATIVE = Set.of("smoke", "large_smoke", "largesmoke", "white_smoke", "campfire_cosy_smoke",
        "campfire_signal_smoke", "ash", "white_ash", "cherry_leaves", "pale_oak_leaves", "falling_spore_blossom", "spore_blossom_air",
        "townaura", "suspended", "depthsuspend");
    /** The potion swirl ids: 1.20 and later, then 1.8.9's names. */
    private static final Set<String> POTION = Set.of("entity_effect", "ambient_entity_effect", "mobspell", "mobspellambient");
    /** The frustum of the frame being drawn (set by the version's level-render hook, cleared when it ends). */
    public static final ParticleFrustum FRUSTUM = new ParticleFrustum();
    /** QA counters: particles dropped at spawn, drawn and culled while drawing, lightmap lookups answered from a particle's last answer. Plain fields; the render thread owns them. */
    public static long dropped, drawn, culled, reused;

    /** QA only: when set, the version hooks add the time spent in the game's particle drawing to {@link #particleNanos}. */
    public static volatile boolean timing;
    private static final java.util.concurrent.atomic.AtomicLong NANOS = new java.util.concurrent.atomic.AtomicLong();
    private static final ThreadLocal<long[]> STARTED = ThreadLocal.withInitial(() -> new long[1]);

    private static volatile boolean standDown;
    private static ParticleCoreModule module;
    private static boolean active, cull, optimized, hideOwn, hideOthers, limit;
    private static double radius;
    private static int perTick;
    private static long seed = System.nanoTime() | 1;
    private static final ClientTools.ParticleBudget BUDGET = new ClientTools.ParticleBudget();

    private ParticleCore() {}

    /** The real Particle Core mod is installed: every hook of ours does nothing and the module is not offered. */
    public static void standDown(boolean value) { standDown = value; refresh(); }
    public static boolean standingDown() { return standDown; }

    public static ParticleCoreModule module() {
        ParticleCoreModule found = module;
        if (found == null) {
            Module registered = ModuleManager.getInstance().getModule(MODULE);
            if (registered instanceof ParticleCoreModule core) module = found = core;
        }
        return found;
    }

    /** Reads the settings into plain fields; the adapters call this once a frame and once a tick, so the per-particle hooks read fields only. */
    public static void refresh() {
        ParticleCoreModule core = module();
        active = core != null && !standDown && core.isEnabled();
        cull = active && core.culling.get();
        optimized = active && core.optimized.get();
        hideOwn = active && core.hideOwn.get();
        hideOthers = active && core.hideOthers.get();
        limit = active && core.limit.get();
        radius = core == null ? 48 : core.distance.getValue();
        perTick = core == null ? 64 : core.perTick.getIntValue();
    }

    public static boolean active() { return active; }
    public static boolean culling() { return cull; }
    public static boolean optimized() { return optimized; }

    /** Between worlds the decorative quota starts over. */
    public static void worldChanged() { BUDGET.reset(); }

    /**
     * One decision per particle about to spawn; false drops it. {@code id} is the type without "minecraft:", {@code own} whether a
     * potion swirl sits on the local player, {@code tick} the world time (the quota's clock) and {@code distanceSq} the squared
     * distance to the player.
     */
    public static boolean allowSpawn(String id, boolean own, double distanceSq, long tick) {
        if (!active) return true;
        if (POTION.contains(id) && (own ? hideOwn : hideOthers)) { dropped++; return false; }
        int percent = module.rates.percent(id);
        if (percent < 100 && !pass(percent, roll())) { dropped++; return false; }
        if (limit && DECORATIVE.contains(id) && !BUDGET.allow(tick, distanceSq, radius, perTick)) { dropped++; return false; }
        return true;
    }

    public static boolean isPotion(String id) { return POTION.contains(id); }

    /** True for {@code roll} (0 to 99) below the share that may spawn: 100 always, 0 never. */
    public static boolean pass(int percent, int roll) { return roll < percent; }

    /** A fast xorshift draw, 0 to 99; one stream for the render and tick threads is fine for a spawn share. */
    public static int roll() {
        long x = seed;
        x ^= x << 13; x ^= x >>> 7; x ^= x << 17;
        seed = x;
        return (int) ((x >>> 33) % 100);
    }

    public static void timeBegin() { if (timing) STARTED.get()[0] = System.nanoTime(); }
    public static void timeEnd() { if (timing) NANOS.addAndGet(System.nanoTime() - STARTED.get()[0]); }
    public static long particleNanos() { return NANOS.get(); }

    public static void resetStats() { dropped = drawn = culled = reused = 0; NANOS.set(0); }
}
