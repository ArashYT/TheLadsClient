package com.thelads.core.v1_20_1.perf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Distance-tiered entity animation evaluation and keyframe interpolation for Minecraft 1.20.1.
 * Prevents shared model singleton crosstalk by recording and restoring entity-specific bone poses
 * on skipped animation frames.
 */
public final class EntityAnimationLod1201 {
    public static final double TIER_0_DIST_SQ = 16.0 * 16.0; // 256.0 m^2
    public static final double TIER_1_DIST_SQ = 32.0 * 32.0; // 1024.0 m^2

    private static final MethodHandle SHADOW_PASS = bindShadowPass();
    private static final Map<Integer, PoseCacheEntry> POSE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> REFLECTED_PART_FIELDS = new ConcurrentHashMap<>();
    private static final Map<EntityModel<?>, List<ModelPart>> MODEL_PARTS = new ConcurrentHashMap<>();
    private static int lastCleanupTick;
    private static int fallbackTick;

    private EntityAnimationLod1201() {}

    /**
     * Determines whether Iris/Oculus is currently rendering a shadow pass.
     */
    public static boolean isShadowPass() {
        if (SHADOW_PASS == null) return false;
        try {
            return (boolean) SHADOW_PASS.invoke();
        } catch (Throwable failure) {
            return false;
        }
    }

    /**
     * Returns the global client game time tick counter, or fallback tick when level is null.
     */
    public static int getClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            return (int) (mc.level.getGameTime() & 0x7FFFFFFF);
        }
        return fallbackTick;
    }

    /**
     * Sets fallback tick counter (for test suites and headless runs).
     */
    public static void setFallbackTick(int tick) {
        fallbackTick = tick;
    }

    /**
     * Clears all cached poses (called on dimension transition or world unload).
     */
    public static void clearCache() {
        POSE_CACHE.clear();
        lastCleanupTick = 0;
    }

    /**
     * Calculates squared distance between an entity and the active main camera.
     */
    public static double getCameraDistanceSq(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return 0.0;
        }
        double cx = mc.gameRenderer.getMainCamera().getPosition().x;
        double cy = mc.gameRenderer.getMainCamera().getPosition().y;
        double cz = mc.gameRenderer.getMainCamera().getPosition().z;

        double dx = entity.getX() - cx;
        double dy = entity.getY() - cy;
        double dz = entity.getZ() - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Determines whether model animations (prepareMobModel and setupAnim) should be evaluated this frame.
     */
    public static boolean shouldEvaluateModel(LivingEntity entity, float partialTick, int currentTick) {
        if (isShadowPass()) return true;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return true;

        // Inviolable Tier 0 exemptions (100% full-rate evaluation)
        if (entity == mc.cameraEntity || entity instanceof Player || entity == mc.crosshairPickEntity) {
            return true;
        }
        if (entity.isCurrentlyGlowing() || entity.hasCustomName() || (entity instanceof net.minecraft.world.entity.Mob mob && mob.isLeashed()) || entity.isPassenger()) {
            return true;
        }
        if (mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return true;
        }

        // Must evaluate on first visible frame to seed pose cache and prevent model crosstalk
        if (!POSE_CACHE.containsKey(entity.getId())) {
            return true;
        }

        double distSq = getCameraDistanceSq(entity);

        // Tier 0: d <= 16.0m (100% full-rate precision)
        if (distSq <= TIER_0_DIST_SQ) {
            return true;
        }

        // Tier 1: 16.0m < d <= 32.0m (evaluate every 2nd tick)
        if (distSq <= TIER_1_DIST_SQ) {
            return (currentTick & 1) == 0;
        }

        // Tier 2: d > 32.0m (evaluate every 4th tick)
        return (currentTick & 3) == 0;
    }

    /**
     * Records the evaluated model part rotations and positions into the entity's pose cache.
     */
    public static void recordPose(LivingEntity entity, EntityModel<?> model, int currentTick) {
        int id = entity.getId();
        PoseCacheEntry entry = POSE_CACHE.computeIfAbsent(id, k -> new PoseCacheEntry());
        entry.record(model, currentTick);
    }

    /**
     * Re-applies cached and interpolated poses into the model parts on skipped frames.
     */
    public static void applyInterpolatedPose(LivingEntity entity, EntityModel<?> model, float partialTick, int currentTick) {
        PoseCacheEntry entry = POSE_CACHE.get(entity.getId());
        if (entry != null) {
            double distSq = getCameraDistanceSq(entity);
            int interval = (distSq > TIER_1_DIST_SQ) ? 4 : 2;
            entry.apply(model, partialTick, currentTick, interval);
        }
    }

    /**
     * Periodic cleanup of stale cached entities (runs every 100 ticks).
     */
    public static void tickCleanup(int currentTick) {
        if (currentTick - lastCleanupTick > 100 || currentTick < lastCleanupTick) {
            lastCleanupTick = currentTick;
            POSE_CACHE.entrySet().removeIf(e -> currentTick - e.getValue().lastTick > 60 || e.getValue().lastTick > currentTick);
        }
    }

    /**
     * Extracts and caches all ModelPart nodes for an EntityModel instance.
     */
    public static List<ModelPart> extractParts(EntityModel<?> model) {
        return MODEL_PARTS.computeIfAbsent(model, EntityAnimationLod1201::resolveParts);
    }

    private static List<ModelPart> resolveParts(EntityModel<?> model) {
        List<ModelPart> parts = new ArrayList<>();
        if (model instanceof HierarchicalModel<?> hierarchical) {
            ModelPart root = hierarchical.root();
            if (root != null) {
                root.getAllParts().forEach(parts::add);
            }
            return parts;
        }

        if (model instanceof AgeableListModel<?> ageable) {
            try {
                // Read headParts and bodyParts via reflection or access
                MethodHandle headMethod = resolveMethod(model.getClass(), "headParts");
                MethodHandle bodyMethod = resolveMethod(model.getClass(), "bodyParts");
                if (headMethod != null) {
                    Iterable<?> headIterable = (Iterable<?>) headMethod.invoke(model);
                    if (headIterable != null) {
                        for (Object p : headIterable) if (p instanceof ModelPart mp) mp.getAllParts().forEach(parts::add);
                    }
                }
                if (bodyMethod != null) {
                    Iterable<?> bodyIterable = (Iterable<?>) bodyMethod.invoke(model);
                    if (bodyIterable != null) {
                        for (Object p : bodyIterable) if (p instanceof ModelPart mp) mp.getAllParts().forEach(parts::add);
                    }
                }
                if (!parts.isEmpty()) return parts;
            } catch (Throwable ignored) {}
        }

        // Generic fallback: inspect fields of type ModelPart
        List<Field> fields = REFLECTED_PART_FIELDS.computeIfAbsent(model.getClass(), clazz -> {
            List<Field> list = new ArrayList<>();
            Class<?> c = clazz;
            while (c != null && c != Object.class && c != EntityModel.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (ModelPart.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        list.add(f);
                    }
                }
                c = c.getSuperclass();
            }
            return list;
        });

        for (Field f : fields) {
            try {
                Object val = f.get(model);
                if (val instanceof ModelPart mp) {
                    mp.getAllParts().forEach(parts::add);
                }
            } catch (Throwable ignored) {}
        }

        return parts;
    }

    private static MethodHandle resolveMethod(Class<?> clazz, String name) {
        try {
            return MethodHandles.lookup().findVirtual(clazz, name, MethodType.methodType(Iterable.class));
        } catch (Throwable t) {
            return null;
        }
    }

    private static MethodHandle bindShadowPass() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                .findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class))
                .bindTo(instance);
        } catch (Throwable absent) {
            return null;
        }
    }

    public static final class PoseCacheEntry {
        public int lastTick;
        private float[] prevRot = new float[64];
        private float[] currRot = new float[64];
        private float[] prevPos = new float[64];
        private float[] currPos = new float[64];
        private int partCount;

        public void record(EntityModel<?> model, int currentTick) {
            List<ModelPart> parts = extractParts(model);
            int count = parts.size();
            if (count == 0) return;

            ensureCapacity(count * 3);

            boolean first = (partCount == 0 || prevRot == null);

            if (!first && currentTick != lastTick) {
                System.arraycopy(currRot, 0, prevRot, 0, Math.min(partCount, count) * 3);
                System.arraycopy(currPos, 0, prevPos, 0, Math.min(partCount, count) * 3);
                lastTick = currentTick;
            }

            int idx = 0;
            for (int i = 0; i < count; i++) {
                ModelPart part = parts.get(i);
                currRot[idx]     = part.xRot;
                currRot[idx + 1] = part.yRot;
                currRot[idx + 2] = part.zRot;
                currPos[idx]     = part.x;
                currPos[idx + 1] = part.y;
                currPos[idx + 2] = part.z;
                idx += 3;
            }
            partCount = count;

            if (first) {
                System.arraycopy(currRot, 0, prevRot, 0, count * 3);
                System.arraycopy(currPos, 0, prevPos, 0, count * 3);
                lastTick = currentTick;
            }
        }

        public void apply(EntityModel<?> model, float partialTick, int currentTick) {
            apply(model, partialTick, currentTick, 2);
        }

        public void apply(EntityModel<?> model, float partialTick, int currentTick, int interval) {
            if (partCount == 0) return;
            List<ModelPart> parts = extractParts(model);
            int count = Math.min(parts.size(), partCount);
            if (count == 0) return;

            float tickDiff = currentTick - lastTick;
            float alpha = Math.max(0.0f, Math.min(1.0f, (partialTick + tickDiff) / (float) interval));

            int idx = 0;
            for (int i = 0; i < count; i++) {
                ModelPart part = parts.get(i);
                part.xRot = lerpAngle(prevRot[idx], currRot[idx], alpha);
                part.yRot = lerpAngle(prevRot[idx + 1], currRot[idx + 1], alpha);
                part.zRot = lerpAngle(prevRot[idx + 2], currRot[idx + 2], alpha);
                part.x    = prevPos[idx]     + alpha * (currPos[idx]     - prevPos[idx]);
                part.y    = prevPos[idx + 1] + alpha * (currPos[idx + 1] - prevPos[idx + 1]);
                part.z    = prevPos[idx + 2] + alpha * (currPos[idx + 2] - prevPos[idx + 2]);
                idx += 3;
            }
        }

        private void ensureCapacity(int needed) {
            if (currRot.length < needed) {
                int newSize = Math.max(needed, currRot.length * 2);
                prevRot = Arrays.copyOf(prevRot, newSize);
                currRot = Arrays.copyOf(currRot, newSize);
                prevPos = Arrays.copyOf(prevPos, newSize);
                currPos = Arrays.copyOf(currPos, newSize);
            }
        }

        private static float lerpAngle(float start, float end, float t) {
            float diff = (end - start) % 6.2831853f;
            if (diff < -3.14159265f) diff += 6.2831853f;
            if (diff > 3.14159265f) diff -= 6.2831853f;
            return start + diff * t;
        }
    }
}
