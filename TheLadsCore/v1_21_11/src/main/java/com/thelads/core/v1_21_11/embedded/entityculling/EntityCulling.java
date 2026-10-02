package com.thelads.core.v1_21_11.embedded.entityculling;

import com.thelads.core.v1_21_11.embedded.entityculling.occlusion.OcclusionCullingInstance;
import com.thelads.core.v1_21_11.embedded.entityculling.occlusion.util.Vec3d;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
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
 * Entity Culling (independent Lads implementation, see NOTICE.txt): a background thread ray-casts from the camera
 * through the client world and marks entities and block entities whose boxes are fully hidden behind opaque blocks;
 * the render thread then skips those that vanilla would otherwise draw. Results are tied to the camera position they
 * were computed from and expire quickly, so anything that may have become visible is drawn until rechecked. Close,
 * large, glowing, named, leashed, riding/ridden, beam, projectile, display and player entities always draw, as do
 * block entities that render off-screen (beacons and the like), and nothing is culled in Iris's shadow pass.
 */
public final class EntityCulling {
    private static final Logger LOGGER = LoggerFactory.getLogger("EntityCulling");
    private static final boolean QA = Boolean.getBoolean("thelads.verifyAutoWorld");
    /** Occluders further than this from the camera are ignored, and boxes beyond it are never culled. */
    private static final int REACH = 64;
    private static final double NEAR_SQ = 4 * 4;
    private static final double MAX_EDGE = 6;
    /** A new camera version starts once the camera is this far from where the current one began. */
    private static final double CAMERA_STEP = 0.5;
    private static final long MAX_AGE = 100_000_000L, FORGET = 1_000_000_000L, PASS = 10_000_000L, IDLE = 50_000_000L;
    private static final MethodHandle IRIS_SHADOW = irisShadowPass();
    private static final ConcurrentLinkedQueue<Cullable> ADDED = new ConcurrentLinkedQueue<>();
    private static final AtomicLong DRAWN = new AtomicLong(), CULLED = new AtomicLong(), BE_DRAWN = new AtomicLong(), BE_CULLED = new AtomicLong();

    private record View(ClientLevel level, double x, double y, double z, int version) {}

    private static volatile View view;
    private static volatile Thread worker;
    // Render thread only.
    private static WeakReference<ClientLevel> anchorLevel = new WeakReference<>(null);
    private static double anchorX, anchorY, anchorZ;
    private static int version;
    private static boolean hookLogged;

    private EntityCulling() {}

    /** True while Iris renders its shadow map: shadows of hidden things must still be cast. */
    public static boolean shadowPass() {
        if (IRIS_SHADOW == null) return false;
        try {
            return (boolean) IRIS_SHADOW.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Render thread: the camera of the main pass, as handed to the entity visibility check. */
    public static void camera(double x, double y, double z) {
        if (QA && !hookLogged) {
            hookLogged = true;
            LOGGER.info("Entity culling: entity render hook active");
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
        if (exempt(entity)) return true;
        boolean hidden = hidden((Cullable) entity);
        if (QA) (hidden ? CULLED : DRAWN).incrementAndGet();
        return !hidden;
    }

    /** Render thread, for a block entity vanilla is about to draw (callers skip off-screen renderers). */
    public static boolean drawBlockEntity(BlockEntity blockEntity) {
        boolean hidden = hidden((Cullable) blockEntity);
        if (QA) (hidden ? BE_CULLED : BE_DRAWN).incrementAndGet();
        return !hidden;
    }

    private static boolean exempt(Entity entity) {
        if (entity == Minecraft.getInstance().getCameraEntity() || entity instanceof Player || entity instanceof Display
            || entity instanceof Projectile || entity instanceof Guardian || entity instanceof EndCrystal) return true;
        if (entity.isCurrentlyGlowing() || entity.hasCustomName() || entity.isPassenger() || entity.isVehicle()) return true;
        if (entity instanceof Leashable leashable && leashable.isLeashed()) return true;
        AABB box = entity.getBoundingBox();
        return box.getXsize() > MAX_EDGE || box.getYsize() > MAX_EDGE || box.getZsize() > MAX_EDGE;
    }

    private static boolean hidden(Cullable target) {
        long now = System.nanoTime();
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
        private long nextReport, lastPass;
        private boolean warned, reportStarted;

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
                lastPass = System.nanoTime() - start;
                if (QA) report(start);
                LockSupport.parkNanos(tracked.isEmpty() ? IDLE : Math.max(2_000_000L, PASS - lastPass));
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
            if (current.x != lastX || current.y != lastY || current.z != lastZ) {
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

        private void report(long now) {
            if (reportStarted && now - nextReport < 0) return;
            boolean first = !reportStarted;
            reportStarted = true;
            nextReport = now + 10_000_000_000L;
            if (first) return;
            LOGGER.info("Entity culling: entities {} drawn / {} culled, block entities {} drawn / {} culled, tracking {}, last pass {} us",
                DRAWN.getAndSet(0), CULLED.getAndSet(0), BE_DRAWN.getAndSet(0), BE_CULLED.getAndSet(0), tracked.size(), lastPass / 1000);
        }
    }

    /** QA only: the ray caster against a synthetic wall, and that the culling state was merged into the game classes. */
    public static void selfTest() {
        OcclusionCullingInstance test = new OcclusionCullingInstance(REACH, new com.thelads.core.v1_21_11.embedded.entityculling.occlusion.DataProvider() {
            @Override public boolean prepareChunk(int chunkX, int chunkZ) { return true; }
            @Override public boolean isOpaqueFullCube(int x, int y, int z) { return x == 5; }
        });
        Vec3d camera = new Vec3d(0.5, 64.5, 0.5);
        boolean hidden = !test.isAABBVisible(new Vec3d(10, 64, 0), new Vec3d(11, 65, 1), camera);
        boolean open = test.isAABBVisible(new Vec3d(-11, 64, 0), new Vec3d(-10, 65, 1), camera);
        LOGGER.info("Entity culling self-test: box behind a wall hidden {}, open box visible {}, state on entities {}, on block entities {}",
            hidden ? "PASS" : "FAIL", open ? "PASS" : "FAIL", Cullable.class.isAssignableFrom(Entity.class) ? "merged" : "MISSING",
            Cullable.class.isAssignableFrom(BlockEntity.class) ? "merged" : "MISSING");
    }

    private static MethodHandle irisShadowPass() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class)).bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Iris is loaded but its shadow-pass API is unavailable; entity culling stays off while it renders shadows", e);
            return MethodHandles.constant(boolean.class, true);
        }
    }
}
