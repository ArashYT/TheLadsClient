package com.thelads.core.v26_2.feature.async;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.thelads.core.modules.AsyncModule;
import com.thelads.core.modules.AsyncSelfCheck;
import com.thelads.core.modules.AsyncTickPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.entity.EntityTickList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async (Lads remake): ticks entities of the integrated server on several threads.
 *
 * Each tick, entities that are cheap to reason about (dropped items, experience orbs and ordinary mobs that are not riding,
 * ridden, leashed, tamed, in a portal, raiding, a boss or a block/teleport/container mob) are grouped by 2x2-chunk region.
 * Regions tick in four checkerboard phases, so regions ticking at the same time are at least 32 blocks apart; everything
 * else ticks first on the main thread exactly as before. While a phase runs, the shared world structures stay safe:
 * entity add/move/remove and client block updates are queued and applied on the main thread when the phase ends, block,
 * tick, POI, stat, advancement, game-event and loot-sequence writes take one lock, chunks are read without loading, and
 * the level random is thread safe. Any exception or unsafe access logs once and turns parallel ticking off until the
 * next world. A self-check per dimension (AsyncSelfCheck) keeps parallel ticking only while it is faster than normal ticking.
 */
public final class AsyncTicking {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsAsync");
    /** Taken only by worker threads, around shared-world writes; the main thread waits while workers run. */
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final List<Worker> WORKERS = new CopyOnWriteArrayList<>();
    private static volatile String fallback;
    private static boolean active;
    private static ExecutorService pool;
    private static int poolSize;
    /** Server thread only: each dimension's self-check, from the first tick Async could run there until the server stops. */
    private static final Map<ServerLevel, AsyncSelfCheck> CHECKS = new HashMap<>();
    private static int usableCores;
    private static long coresCheckedAt;
    /** QA counters. */
    static final AtomicLong PARALLEL_TICKS = new AtomicLong(), PARALLEL_ENTITIES = new AtomicLong(), REGIONS = new AtomicLong(), PHASES = new AtomicLong();
    /** QA: time spent in the entity loop of every dimension, Async on or off. */
    static final AtomicLong LOOP_NANOS = new AtomicLong();
    /** QA: self-check switches (parallel ticking benched, and back). */
    static final AtomicLong BENCHED = new AtomicLong(), RESUMED = new AtomicLong();
    /** QA: each worker task first sleeps this long, as if the OS kept it waiting for a busy core (simulated outside CPU load). */
    static volatile long qaStallNanos;

    private AsyncTicking() {}

    /** Client init. A loaded external Async mod ticks entities itself, so the Lads one stays out (its mixins never apply). */
    public static void initialize() {
        if (active || FabricLoader.getInstance().isModLoaded("async")) return;
        active = true;
        ModuleSupport.registerBuiltIn(AsyncModule.NAME);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { fallback = null; CHECKS.clear(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { shutdown(); CHECKS.clear(); });
        AsyncStressProbe.initialize();
    }

    public static boolean onWorker() { return Thread.currentThread() instanceof Worker; }

    /** Queue a main-thread action for the end of the running phase (worker threads only). */
    public static void defer(Runnable action) { ((Worker) Thread.currentThread()).deferred.add(action); }

    /** Something a worker cannot do without the main thread: aborts this entity's tick and ends parallel ticking. */
    public static RuntimeException unsafe(String what) { return new IllegalStateException("Lads Async: unsafe off-thread access, " + what); }

    public static <T> T locked(Operation<T> original, Object... args) {
        if (!onWorker()) return original.call(args);
        LOCK.lock();
        try { return original.call(args); } finally { LOCK.unlock(); }
    }

    /** As locked, and the stream is read while the lock is held. */
    public static <T> Stream<T> lockedStream(Operation<Stream<T>> original, Object... args) {
        if (!onWorker()) return original.call(args);
        LOCK.lock();
        try { return original.call(args).toList().stream(); } finally { LOCK.unlock(); }
    }

    public static void lock() { LOCK.lock(); }
    public static void unlock() { LOCK.unlock(); }

    public static String fallback() { return fallback; }

    /** ServerLevel.tick's entity loop. {@code action} is vanilla's per-entity lambda, run unchanged on whichever thread. */
    public static void tickEntities(ServerLevel level, EntityTickList list, Consumer<Entity> action, Operation<Void> original) {
        long start = System.nanoTime();
        try { tick(level, list, action, original); } finally { LOOP_NANOS.addAndGet(System.nanoTime() - start); }
    }

    private static void tick(ServerLevel level, EntityTickList list, Consumer<Entity> action, Operation<Void> original) {
        AsyncModule module = ModuleManager.getInstance().getModule(AsyncModule.NAME) instanceof AsyncModule async ? async : null;
        int threads = module == null ? 1 : module.threads(usableCores());
        // An active profiler (F3 profiling, /debug) is one per thread: its records would be torn apart.
        if (!active || module == null || !module.isEnabled() || threads < 2 || fallback != null
            || level.getServer().isDedicatedServer() || Profiler.get() != InactiveProfiler.INSTANCE) {
            original.call(list, action);
            return;
        }
        List<Entity> entities = new ArrayList<>();
        list.forEach(entities::add);
        if (entities.size() < module.minEntities()) { original.call(list, action); return; }
        AsyncSelfCheck check = CHECKS.computeIfAbsent(level, key -> new AsyncSelfCheck());
        long start = System.nanoTime();
        if (check.parallel()) tickParallel(entities, action, threads);
        else original.call(list, action);
        AsyncSelfCheck.Change change = check.record(System.nanoTime() - start, entities.size());
        if (change == AsyncSelfCheck.Change.BENCHED) {
            BENCHED.incrementAndGet();
            LOGGER.info("Lads Async: parallel entity ticking was not faster in {} ({}); ticking normally, trying again in {} minutes",
                level.dimension().identifier(), compared(check), check.backoffTicks() / 1200);
        } else if (change == AsyncSelfCheck.Change.RESUMED) {
            RESUMED.incrementAndGet();
            LOGGER.info("Lads Async: parallel entity ticking is faster again in {} ({}); back on", level.dimension().identifier(), compared(check));
        }
    }

    private static String compared(AsyncSelfCheck check) {
        return String.format(Locale.ROOT, "%.1f ms parallel vs %.1f ms normal per 1000 entities", check.lastParallel() / 1000, check.lastNormal() / 1000);
    }

    /** QA: whether the self-check has parallel ticking switched off in this dimension. */
    static boolean benched(ServerLevel level) {
        AsyncSelfCheck check = CHECKS.get(level);
        return check != null && check.benched();
    }

    /**
     * Cores this game may run on. On Windows Java counts every core even when the process is limited to a few (affinity set by
     * the user or another program), so the process affinity mask is read too; elsewhere availableProcessors already follows it.
     */
    static int usableCores() {
        long now = System.nanoTime();
        if (usableCores > 0 && now - coresCheckedAt < 30_000_000_000L) return usableCores;
        int cores = Runtime.getRuntime().availableProcessors();
        try {
            if (Platform.isWindows()) {
                BaseTSD.ULONG_PTRByReference process = new BaseTSD.ULONG_PTRByReference(), system = new BaseTSD.ULONG_PTRByReference();
                if (Kernel32.INSTANCE.GetProcessAffinityMask(Kernel32.INSTANCE.GetCurrentProcess(), process, system)) {
                    int allowed = Long.bitCount(process.getValue().longValue());
                    if (allowed > 0) cores = Math.min(cores, allowed);
                }
            }
        } catch (Throwable ignored) {
            // no JNA or no answer: availableProcessors
        }
        usableCores = cores;
        coresCheckedAt = now;
        return cores;
    }

    private static void tickParallel(List<Entity> entities, Consumer<Entity> action, int threads) {
        AsyncTickPlan<Entity> plan = AsyncTickPlan.of(entities, AsyncTicking::mayRunInParallel,
            entity -> entity.chunkPosition().x(), entity -> entity.chunkPosition().z());
        for (Entity entity : plan.sequential) action.accept(entity);
        ExecutorService workers = pool(threads);
        PARALLEL_TICKS.incrementAndGet();
        for (int phase = 0; phase < AsyncTickPlan.PHASES; phase++) {
            List<List<Entity>> regions = plan.phase(phase);
            if (regions.size() < 2 || fallback != null) {
                for (List<Entity> region : regions) region.forEach(action);
                continue;
            }
            int[] done = new int[regions.size()];
            List<Callable<Void>> tasks = new ArrayList<>(regions.size());
            for (int i = 0; i < regions.size(); i++) {
                List<Entity> region = regions.get(i);
                int index = i;
                tasks.add(() -> {
                    long stall = qaStallNanos;
                    if (stall > 0) java.util.concurrent.locks.LockSupport.parkNanos(stall);
                    for (Entity entity : region) {
                        if (fallback != null) break;
                        try { action.accept(entity); } catch (Throwable failure) { fail(entity, failure); }
                        done[index]++;
                    }
                    return null;
                });
            }
            try {
                workers.invokeAll(tasks);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail(null, interrupted);
            }
            drain();
            PHASES.incrementAndGet();
            REGIONS.addAndGet(regions.size());
            // After a failure the entities no worker reached still tick this tick, here.
            if (fallback != null) for (int i = 0; i < regions.size(); i++) regions.get(i).subList(done[i], regions.get(i).size()).forEach(action);
            else PARALLEL_ENTITIES.addAndGet(regions.stream().mapToInt(List::size).sum());
        }
    }

    /** Entities whose whole tick stays within a few blocks and touches only what the guards above cover. */
    static boolean mayRunInParallel(Entity entity) {
        if (entity.isPassenger() || entity.isVehicle() || entity.portalProcess != null) return false;
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) return true;
        if (!(entity instanceof Mob mob) || mob.isLeashed() || mob instanceof OwnableEntity owned && owned.getOwnerReference() != null) return false;
        return !(mob instanceof Raider || mob instanceof EnderDragon || mob instanceof WitherBoss || mob instanceof Warden
            || mob instanceof ElderGuardian || mob instanceof EnderMan || mob instanceof Shulker || mob instanceof Bee
            || mob instanceof Allay || mob instanceof HappyGhast || mob instanceof Dolphin || mob instanceof Creaking
            || mob instanceof CopperGolem);
    }

    private static void drain() {
        for (Worker worker : WORKERS) {
            List<Runnable> actions = worker.deferred;
            for (int i = 0; i < actions.size(); i++) {
                try { actions.get(i).run(); } catch (Throwable failure) { fail(null, failure); }
            }
            actions.clear();
        }
    }

    private static void fail(Entity entity, Throwable failure) {
        synchronized (AsyncTicking.class) {
            if (fallback != null) return;
            fallback = (entity == null ? "a queued world update" : entity.typeHolder().getRegisteredName()) + ": " + failure;
        }
        LOGGER.error("Lads Async: parallel entity ticking is off until you rejoin the world, after an error while ticking {}", fallback, failure);
    }

    private static synchronized ExecutorService pool(int size) {
        if (pool != null && poolSize == size) return pool;
        shutdown();
        AtomicInteger count = new AtomicInteger();
        pool = Executors.newFixedThreadPool(size, task -> {
            Worker worker = new Worker(task, "Lads Async Worker #" + count.incrementAndGet());
            WORKERS.add(worker);
            return worker;
        });
        poolSize = size;
        return pool;
    }

    private static synchronized void shutdown() {
        if (pool == null) return;
        pool.shutdownNow();
        pool = null;
        WORKERS.clear();
    }

    private static final class Worker extends Thread {
        final List<Runnable> deferred = new ArrayList<>();

        Worker(Runnable task, String name) {
            super(task, name);
            setDaemon(true);
        }
    }
}
