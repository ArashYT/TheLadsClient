package com.thelads.core.client.audio;

import com.thelads.core.modules.AudioEngineModule;
import com.thelads.core.modules.ExtraModules;
import com.thelads.core.perf.MemoryGuard;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Audio Engine facade for the version mixins: one decision per started sound plus counters and the init log.
 * Everything here runs on the client thread; OpenAL work stays in the version code on the sound executor.
 */
public final class AudioEngine {
    private AudioEngine() {}

    public static final String[] PRELOAD = {
        "entity.arrow.shoot", "entity.arrow.hit", "entity.arrow.hit_player", "entity.player.hurt", "entity.generic.hurt",
        "entity.generic.explode", "entity.ender_pearl.throw", "entity.player.attack.strong", "entity.player.attack.weak",
        "entity.player.attack.crit", "entity.player.attack.sweep", "entity.player.attack.knockback", "entity.player.attack.nodamage",
        "entity.player.burp", "item.shield.block", "item.trident.throw", "item.mace.smash_ground", "item.armor.equip_generic",
        "block.stone.break", "block.stone.place", "block.stone.hit", "block.stone.step", "block.grass.step", "block.wood.step",
        "block.wooden_door.open", "block.wooden_door.close", "block.chest.open", "block.chest.close", "ui.button.click",
        "entity.experience_orb.pickup", "entity.item.pickup", "entity.firework_rocket.blast", "entity.tnt.primed"
    };

    public static final long DEBUG_INTERVAL_MS = 60_000L;

    private static final SoundDedup DEDUP = new SoundDedup();
    private static final Occlusion<Object> OCCLUSION = new Occlusion<Object>();

    private static final AtomicLong evicted = new AtomicLong(), deduped = new AtomicLong(), culled = new AtomicLong(),
        rays = new AtomicLong(), blacklisted = new AtomicLong();
    private static boolean inited;
    private static boolean standDown;
    private static boolean physicsMod;
    private static long lastDebug;
    private static final boolean DEBUG = Boolean.getBoolean("lads.audioDebug") || Boolean.getBoolean("lads.audiodebug");

    /** Idempotent; the first mixin call passes what the loader reports. Logs the single init line and registers the cache trim. */
    public static synchronized void init(boolean audioEnginePlusLoaded, boolean soundPhysicsLoaded, String version, String note) {
        if (inited) return;
        inited = true;
        standDown = audioEnginePlusLoaded;
        physicsMod = soundPhysicsLoaded;
        MemoryGuard.register("AudioEngine", AudioEngine::clearCaches);
        MemoryGuard.registerLevelBound("AudioEngine", () -> { DEDUP.clear(); OCCLUSION.clear(); }); // both are keyed by positions of the old level
        org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("[Lads] AudioEngine {} {}{}", version,
            standDown ? "standing down (audioengineplus loaded)" : "active: pool, dedup, LOD, occlusion"
                + (physicsMod ? " (occlusion off: sound_physics_remastered loaded)" : ""), note == null || note.isEmpty() ? "" : "; " + note);
    }

    public static synchronized boolean isInited() { return inited; }

    /** Cheap pre-check so the version code only builds the playing-sound list when the pool is near full or a category may be over its cap. */
    public static boolean needScan(String usage, int playing) {
        AudioEngineModule m = module();
        int[] u = SourcePool.parseUsage(usage);
        if (u != null && SourcePool.nearLimit(u[0], u[1])) return true;
        int min = Math.min(m.ambientCap.getIntValue(), Math.min(m.hostileCap.getIntValue(), m.blockCap.getIntValue()));
        return playing >= min;
    }

    public static boolean active() { return inited && !standDown && module().isEnabled(); }

    public static AudioEngineModule module() { return ExtraModules.AUDIO_ENGINE; }

    public static boolean occlusionOn() { return active() && !physicsMod && module().occlusion.get(); }

    public static void clearCaches() { DEDUP.clear(); OCCLUSION.clear(); SoundBlacklist.clear(); }

    public static Occlusion<Object> occlusion() { return OCCLUSION; }

    public static void countRays(int n) { if (n > 0) rays.addAndGet(n); }

    /**
     * What to do with a sound that is about to start. Returns 0 to drop it, otherwise the volume multiplier (1 = unchanged).
     * {@code predicted} marks sounds on the prediction path (PredictionMatcher), which are never touched.
     */
    public static float onPlay(String category, String id, String path, boolean positional, boolean looping, boolean tickable,
                               boolean predicted, double x, double y, double z, double dist, double gain, double range, int tick) {
        AudioEngineModule m = module();
        if (predicted) return 1f;
        if (m.blacklist.get() && SoundBlacklist.matches(m.blacklistPatterns.get(), id)) { blacklisted.incrementAndGet(); return 0f; }
        int prio = SoundPriority.classify(category, path, dist);
        boolean exempt = looping || tickable || prio == SoundPriority.MUSIC || !positional;
        if (m.lod.get() && !exempt && prio != SoundPriority.CRITICAL
            && AudioLod.cull(dist, gain, range, m.cullFactor.getValue())) {
            culled.incrementAndGet();
            return 0f;
        }
        if (m.dedup.get() && SoundDedup.dedupable(category, path, positional, looping || tickable)) {
            int r = DEDUP.onSound(tick, id, x, y, z, m.dedupThreshold.getIntValue(), m.dedupDistance.getValue());
            if (r == SoundDedup.DROP) { deduped.incrementAndGet(); return 0f; }
            if (r > 1) return SoundDedup.boost(r);
        }
        return 1f;
    }

    /** Marker: the new sound loses (drop it); returned by {@link #pickEviction}. */
    public static final SourcePool.Candidate DROP_NEW = new SourcePool.Candidate(null, 0, 0, 0, false, false);

    /**
     * Pool pressure check for a new sound of priority {@code prio}. Returns the playing sound to stop first, {@link #DROP_NEW} when
     * the new sound itself should be dropped (its category is at its soft cap and it is the farthest), or null to leave it to vanilla.
     */
    public static SourcePool.Candidate pickEviction(String usage, List<SourcePool.Candidate> playing, int prio, double distSq) {
        AudioEngineModule m = module();
        if (!m.pool.get() || prio == SoundPriority.MUSIC) return null;
        int cap = cap(m, prio);
        if (cap > 0 && SourcePool.count(playing, prio) >= cap) {
            SourcePool.Candidate c = SourcePool.pickCapVictim(playing, prio, distSq);
            if (c == null) { evicted.incrementAndGet(); return DROP_NEW; }
            evicted.incrementAndGet();
            return c;
        }
        int[] u = SourcePool.parseUsage(usage);
        if (u == null || !SourcePool.nearLimit(u[0], u[1])) return null;
        SourcePool.Candidate c = SourcePool.pickVictim(playing, prio, distSq, false);
        if (c != null) evicted.incrementAndGet();
        return c;
    }

    private static int cap(AudioEngineModule m, int prio) {
        switch (prio) {
            case SoundPriority.AMBIENT: return m.ambientCap.getIntValue();
            case SoundPriority.HOSTILE: return m.hostileCap.getIntValue();
            case SoundPriority.NEUTRAL: return m.blockCap.getIntValue();
            default: return 0;
        }
    }

    /** Once per client tick; prints the per-minute counter line under -Dlads.audioDebug=true. */
    public static void tickDebug(long nowMs) {
        if (!DEBUG) return;
        if (lastDebug == 0) { lastDebug = nowMs; return; }
        if (nowMs - lastDebug < DEBUG_INTERVAL_MS) return;
        lastDebug = nowMs;
        org.slf4j.LoggerFactory.getLogger("TheLadsCore").info("[Lads] AudioEngine/min evicted={} deduped={} culled={} blacklisted={} occlusionRays={}",
            evicted.getAndSet(0), deduped.getAndSet(0), culled.getAndSet(0), blacklisted.getAndSet(0), rays.getAndSet(0));
    }
}
