package com.thelads.core.v1_20_1.embedded.entityculling;

import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.v1_20_1.embedded.entityculling.occlusion.OcclusionCullingInstance;
import com.thelads.core.v1_20_1.embedded.entityculling.occlusion.util.Vec3d;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entity Culling for Minecraft 1.20.1 (independent Lads implementation, see NOTICE.txt; ported from the Lads 26.3 copy): a
 * background thread ray-casts from the camera through the client world and marks entities and block entities whose boxes
 * are fully hidden behind opaque blocks; the render thread then skips those that vanilla would otherwise draw. Results are
 * tied to the camera position they were computed from and expire quickly, so anything that may have become visible is drawn
 * until rechecked. Close, large, glowing, named, leashed, riding/ridden, beam, projectile, display and player entities always
 * draw, as do block entities that render off-screen (beacons and the like), and nothing is culled while a shader pack
 * (Iris or Oculus) renders its shadow map.
 */
public final class EntityCulling {
    private static final Logger LOGGER = LoggerFactory.getLogger("EntityCulling");
    /** QA probe (-Dthelads.perfProbe=true): count what the hooks see and log it; off, the hooks only pay for the checks. */
    private static final boolean PROBE = com.thelads.core.v1_20_1.perf.PerfProbe1201.passive();
    /** Occluders further than this from the camera are ignored, and boxes beyond it are never culled. */
    private static final int REACH = 64;
    private static final double NEAR_SQ = 4 * 4;
    private static final double MAX_EDGE = 6;
    /** A new camera version starts once the camera is this far from where the current one began. */
    private static final double CAMERA_STEP = 0.5;
    private static final long MAX_AGE = 100_000_000L, FORGET = 1_000_000_000L, PASS = 10_000_000L, IDLE = 50_000_000L;
    private static final ConcurrentLinkedQueue<Cullable> ADDED = new ConcurrentLinkedQueue<>();
    private static final AtomicLong EVALUATED = new AtomicLong(), DRAWN = new AtomicLong(), CULLED = new AtomicLong(), BE_DRAWN = new AtomicLong(), BE_CULLED = new AtomicLong();

    private record View(ClientLevel level, double x, double y, double z, int version) {}

    private static volatile View view;
    private static volatile Thread worker;
    private static volatile int trackedCount;
    private static volatile long frameNow = System.nanoTime();
    // Render thread only.
    private static WeakReference<ClientLevel> anchorLevel = new WeakReference<>(null);
    private static double anchorX, anchorY, anchorZ;
    private static int version;
    private static boolean hookLogged, blockHookLogged;

    private EntityCulling() {}

    /** Shader-pack shadow pass detection; resolved on first use, i.e. once a world renders and every mod is loaded. */
    private static final class Shadow {
        /** Which API answered, for the probe line. Declared before STATE: resolve() fills it in. */
        static String api = "none";
        static final MethodHandle STATE = resolve();

        private static MethodHandle resolve() {
            // Iris 1.7+ moved to net.irisshaders; Iris 1.6 and Oculus (Zero SMP) keep the net.coderbot package.
            for (String name : new String[] {"net.irisshaders.iris.shadows.ShadowRenderingState", "net.coderbot.iris.shadows.ShadowRenderingState"}) {
                Class<?> type = find(name);
                if (type == null) continue;
                MethodType signature = MethodType.methodType(boolean.class);
                for (MethodHandles.Lookup lookup : new MethodHandles.Lookup[] {MethodHandles.lookup(), MethodHandles.publicLookup()}) {
                    try {
                        MethodHandle handle = lookup.findStatic(type, "areShadowsCurrentlyBeingRendered", signature);
                        api = name;
                        LOGGER.info("Entity culling: shadow pass detected through {}", name);
                        return handle;
                    } catch (ReflectiveOperationException | LinkageError ignored) {
                        // Next lookup, then next package.
                    }
                }
            }
            if (ModPresence1201.loaded("iris", "oculus")) {
                LOGGER.warn("Entity culling: a shader mod is installed but its shadow-pass state is unavailable; culling stays off");
                api = "unavailable";
                return MethodHandles.constant(boolean.class, true);
            }
            return null;
        }

        private static Class<?> find(String name) {
            for (ClassLoader loader : new ClassLoader[] {EntityCulling.class.getClassLoader(), Thread.currentThread().getContextClassLoader()}) {
                if (loader == null) continue;
                try {
                    return Class.forName(name, false, loader);
                } catch (ClassNotFoundException | LinkageError ignored) {
                    // Try the next loader.
                }
            }
            return null;
        }
    }

    /** True while a shader pack renders its shadow map: shadows of hidden things must still be cast. */
    public static boolean shadowPass() {
        MethodHandle state = Shadow.STATE;
        if (state == null) return false;
        try {
            return (boolean) state.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Render thread: the camera of the main pass, as handed to the entity visibility check. */
    public static void camera(double x, double y, double z) {
        frameNow = System.nanoTime();
        if (worker == null) startWorker();
        if (PROBE && !hookLogged) {
            hookLogged = true;
            LOGGER.info("PERF-PROBE entityculling HOOK entity-render ACTIVE");
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != anchorLevel.get() || Math.abs(x - anchorX) > CAMERA_STEP || Math.abs(y - anchorY) > CAMERA_STEP || Math.abs(z - anchorZ) > CAMERA_STEP) {
            anchorLevel = new WeakReference<>(level);
            anchorX = x;
            anchorY = y;
            anchorZ = z;
            version++;
        }
        View current = view;
        if (current == null || current.level != level || current.x != x || current.y != y || current.z != z || current.version != version)
            view = new View(level, x, y, z, version);
    }

    /** Render thread, for an entity vanilla is about to draw: false when it was found fully occluded. */
    public static boolean drawEntity(Entity entity) {
        if (PROBE) EVALUATED.incrementAndGet();
        if (exempt(entity)) return true;
        boolean hidden = hidden((Cullable) entity);
        if (PROBE) (hidden ? CULLED : DRAWN).incrementAndGet();
        return !hidden;
    }

    /** Render thread, for a block entity vanilla is about to draw (callers skip off-screen renderers). */
    public static boolean drawBlockEntity(BlockEntity blockEntity) {
        if (PROBE && !blockHookLogged) {
            blockHookLogged = true;
            LOGGER.info("PERF-PROBE entityculling HOOK block-entity-render ACTIVE");
        }
        boolean hidden = hidden((Cullable) blockEntity);
        if (PROBE) (hidden ? BE_CULLED : BE_DRAWN).incrementAndGet();
        return !hidden;
    }

    private static boolean exempt(Entity entity) {
        if (entity == Minecraft.getInstance().getCameraEntity() || entity instanceof Player || entity instanceof Display
            || entity instanceof Projectile || entity instanceof Guardian || entity instanceof EndCrystal) return true;
        if (entity.noCulling || entity.isCurrentlyGlowing() || entity.hasCustomName() || entity.isPassenger() || entity.isVehicle()) return true;
        if (com.thelads.core.modules.MobOverlayModule.outlined(com.thelads.core.v1_20_1.game.pvp.PvpVisuals1201.mobKind(entity))) return true; // Mob Overlay's outline, as glowing
        if (entity instanceof Mob mob && mob.isLeashed()) return true;
        AABB box = entity.getBoundingBox();
        return box.getXsize() > MAX_EDGE || box.getYsize() > MAX_EDGE || box.getZsize() > MAX_EDGE;
    }

    private static boolean hidden(Cullable target) {
        long now = frameNow;
        target.lads$seen(now);
        if (!target.lads$tracked()) {
            target.lads$tracked(true);
            ADDED.add(target);
            if (worker == null) startWorker();
        }
        View current = view;
        return current != null && target.lads$hiddenVersion() == current.version && now - target.lads$checkedAt() < MAX_AGE;
    }

    private static synchronized void startWorker() {
        if (worker != null) return;
        Thread thread = new Thread(new Pass(), "Lads Entity Culling");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
        worker = thread;
        LOGGER.info("Entity culling worker started");
    }

    private static final class Pass implements Runnable {
        private final LevelOcclusionData data = new LevelOcclusionData();
        private final OcclusionCullingInstance culling = new OcclusionCullingInstance(REACH, data);
        private final ArrayList<Cullable> tracked = new ArrayList<>();
        private final Vec3d min = new Vec3d(0, 0, 0), max = new Vec3d(0, 0, 0), viewer = new Vec3d(0, 0, 0);
        private ClientLevel level;
        private double lastX = Double.NaN, lastY, lastZ;
        private boolean warned;

        @Override
        public void run() {
            while (true) {
                long start = System.nanoTime();
                try {
                    pass(start);
                } catch (Throwable t) {
                    if (!warned) LOGGER.warn("Entity culling pass failed; it keeps retrying", t);
                    warned = true;
                }
                trackedCount = tracked.size();
                long took = System.nanoTime() - start;
                LockSupport.parkNanos(tracked.isEmpty() ? IDLE : Math.max(2_000_000L, PASS - took));
            }
        }

        private void pass(long now) {
            View current = view;
            if (current != null && current.level != Minecraft.getInstance().level) {
                // Left the world (or switched it): drop every reference to the old one.
                view = null;
                current = null;
            }
            if (current == null || current.level != level) {
                level = current == null ? null : current.level;
                for (Cullable target : tracked) target.lads$tracked(false);
                tracked.clear();
                culling.resetCache();
                lastX = Double.NaN;
                data.level(level);
                if (current == null) {
                    for (Cullable added; (added = ADDED.poll()) != null; ) added.lads$tracked(false);
                    return;
                }
            }
            for (Cullable added; (added = ADDED.poll()) != null; ) tracked.add(added);
            if (Double.isNaN(lastX) || Math.abs(current.x - lastX) + Math.abs(current.y - lastY) + Math.abs(current.z - lastZ) > 0.05) {
                culling.resetCache();
                lastX = current.x;
                lastY = current.y;
                lastZ = current.z;
            }
            data.level(level);
            viewer.set(current.x, current.y, current.z);
            for (int i = tracked.size() - 1; i >= 0; i--) {
                Cullable target = tracked.get(i);
                if (now - target.lads$seenAt() > FORGET) {
                    target.lads$tracked(false);
                    tracked.set(i, tracked.get(tracked.size() - 1));
                    tracked.remove(tracked.size() - 1);
                    continue;
                }
                boolean visible;
                if (target instanceof Entity entity) {
                    AABB box = entity.getBoundingBox();
                    Vec3 motion = entity.getDeltaMovement();
                    double grow = Math.min(4, motion.length() * 2);
                    visible = visible(current, box.minX - grow, box.minY - grow, box.minZ - grow, box.maxX + grow, box.maxY + grow, box.maxZ + grow);
                } else {
                    BlockPos pos = ((BlockEntity) target).getBlockPos();
                    // One block of slack on every side covers double chests, beds, banners and other models wider than their block.
                    visible = visible(current, pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1, pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
                }
                target.lads$cullResult(visible ? 0 : current.version, System.nanoTime());
            }
        }

        private boolean visible(View camera, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            double dx = Math.max(0, Math.max(minX - camera.x, camera.x - maxX));
            double dy = Math.max(0, Math.max(minY - camera.y, camera.y - maxY));
            double dz = Math.max(0, Math.max(minZ - camera.z, camera.z - maxZ));
            if (dx * dx + dy * dy + dz * dz < NEAR_SQ) return true;
            int limit = REACH - 4;
            if (Math.max(Math.abs(minX - camera.x), Math.abs(maxX - camera.x)) > limit || Math.max(Math.abs(minY - camera.y), Math.abs(maxY - camera.y)) > limit
                || Math.max(Math.abs(minZ - camera.z), Math.abs(maxZ - camera.z)) > limit) return true;
            min.set(minX, minY, minZ);
            max.set(maxX, maxY, maxZ);
            return culling.isAABBVisible(min, max, viewer);
        }
    }

    /** QA probe: the ray caster against a synthetic wall, and that the culling state was merged into the game classes. */
    public static boolean selfTest() {
        OcclusionCullingInstance test = new OcclusionCullingInstance(REACH, new com.thelads.core.v1_20_1.embedded.entityculling.occlusion.DataProvider() {
            @Override public boolean prepareChunk(int chunkX, int chunkZ) { return true; }
            @Override public boolean isOpaqueFullCube(int x, int y, int z) { return x == 5; }
        });
        Vec3d camera = new Vec3d(0.5, 64.5, 0.5);
        boolean hidden = !test.isAABBVisible(new Vec3d(10, 64, 0), new Vec3d(11, 65, 1), camera);
        boolean open = test.isAABBVisible(new Vec3d(-11, 64, 0), new Vec3d(-10, 65, 1), camera);
        boolean entities = Cullable.class.isAssignableFrom(Entity.class), blockEntities = Cullable.class.isAssignableFrom(BlockEntity.class);
        boolean pass = hidden && open && entities && blockEntities;
        LOGGER.info("PERF-PROBE entityculling SELFTEST {} wallHidden={} openVisible={} cullableOnEntity={} cullableOnBlockEntity={}",
            pass ? "PASS" : "FAIL", hidden, open, entities, blockEntities);
        return pass;
    }

    /** QA probe, once in a world every 600 ticks: counters since the last line; PASS when entities were evaluated and the worker runs. */
    public static void probeReport() {
        Thread thread = worker;
        long evaluated = EVALUATED.getAndSet(0);
        boolean alive = thread != null && thread.isAlive();
        LOGGER.info("PERF-PROBE entityculling {} evaluated={} drawn={} culled={} blockEntitiesDrawn={} blockEntitiesCulled={} tracking={} worker={} shadowApi={}",
            evaluated > 0 && alive ? "PASS" : "FAIL", evaluated, DRAWN.getAndSet(0), CULLED.getAndSet(0), BE_DRAWN.getAndSet(0), BE_CULLED.getAndSet(0),
            trackedCount, thread == null ? "not-started" : alive ? "alive" : "dead", Shadow.api);
    }
}
