package com.thelads.core.v1_20_1.perf;

import com.thelads.core.client.ParticleCore;
import com.thelads.core.client.ParticleFrustum;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Optimizes Minecraft 1.20.1 precipitation rendering (rain and snow columns).
 *
 * <p>1. Frustum-culls columns against {@link ParticleCore#FRUSTUM} before chunk lookups or quad creation.
 * <p>2. Maintains a spatial 16x16 direct-mapped heightmap cache (20-tick TTL) to eliminate redundant
 * {@link Level#getHeight(Heightmap.Types, int, int)} queries across frames.
 */
public final class WeatherOptimizer1201 {
    private static final int CACHE_SIZE = 256;
    private static final long[] TAGS = new long[CACHE_SIZE];
    private static final int[] HEIGHTS = new int[CACHE_SIZE];
    private static final int[] TICKS = new int[CACHE_SIZE];

    private static Level cachedLevel;
    private static double camX, camY, camZ;
    private static int currentTick;
    private static boolean frameActive;

    static {
        clearCache();
    }

    private WeatherOptimizer1201() {}

    /**
     * Initializes camera-relative coordinates and tick timestamp for the active weather frame.
     */
    public static void startFrame(double x, double y, double z, long tick) {
        camX = x;
        camY = y;
        camZ = z;
        currentTick = (int) tick;
        frameActive = true;
    }

    /**
     * Resets the active frame state upon completion of weather rendering.
     */
    public static void endFrame() {
        frameActive = false;
    }

    /**
     * Tests a vertical weather column bounding cylinder against the camera view frustum.
     * Restricts the vertical test interval to the active weather window [cy - 10, cy + 10].
     */
    public static boolean isWeatherColumnVisible(double cx, double cy, double cz, int blockX, int blockZ, int minY, int maxY, ParticleFrustum frustum) {
        if (frustum == null || !frustum.isValid()) {
            return true;
        }

        // Clamp vertical range to the active precipitation render window [cy - 10, cy + 10]
        int activeMinY = Math.max(minY, (int) Math.floor(cy - 10.0));
        int activeMaxY = Math.min(maxY, (int) Math.floor(cy + 10.0));
        if (activeMinY >= activeMaxY) {
            return false;
        }

        double colX = (blockX + 0.5) - cx;
        double colY = ((activeMinY + activeMaxY) * 0.5) - cy;
        double colZ = (blockZ + 0.5) - cz;

        double halfH = (activeMaxY - activeMinY) * 0.5;
        // Conservative horizontal radius of 1.0 block (quad vertex cross-section is ~0.707 blocks)
        return frustum.visibleCylinder(colX, colY, colZ, 1.0, halfH);
    }

    /**
     * Overload accepting 2D camera coordinates and deriving active Y window from cached camY.
     */
    public static boolean isWeatherColumnVisible(double cx, double cz, int blockX, int blockZ, ParticleFrustum frustum) {
        return isWeatherColumnVisible(cx, camY, cz, blockX, blockZ, (int) Math.floor(camY - 10.0), (int) Math.floor(camY + 10.0), frustum);
    }

    /**
     * Retrieves precipitation height, performing view frustum culling and spatial caching.
     * If the column lies wholly outside the view frustum, returns {@link Integer#MAX_VALUE}
     * so the vanilla loop immediately skips quad generation.
     */
    public static int getPrecipitationHeight(Level level, Heightmap.Types type, int x, int z) {
        if (level != cachedLevel) {
            cachedLevel = level;
            clearCache();
        }

        // 1. Frustum culling check
        if (frameActive) {
            ParticleFrustum frustum = ParticleCore.FRUSTUM;
            if (frustum != null && frustum.isValid()) {
                int minY = Math.max(level.getMinBuildHeight(), (int) Math.floor(camY - 10.0));
                int maxY = Math.min(level.getMaxBuildHeight(), (int) Math.floor(camY + 10.0));
                if (!isWeatherColumnVisible(camX, camY, camZ, x, z, minY, maxY, frustum)) {
                    // Returning Integer.MAX_VALUE forces (v > u) in vanilla renderSnowAndRain to be false,
                    // skipping biome lookup and quad generation entirely.
                    return Integer.MAX_VALUE;
                }
            }
        }

        // 2. Spatial 16x16 direct-mapped heightmap cache
        int index = (x & 15) | ((z & 15) << 4);
        long tag = ((long) x << 32) | (z & 0xFFFFFFFFL);

        if (TAGS[index] == tag && (currentTick - TICKS[index]) < 20) {
            return HEIGHTS[index];
        }

        int height = level.getHeight(type, x, z);
        TAGS[index] = tag;
        HEIGHTS[index] = height;
        TICKS[index] = currentTick;
        return height;
    }

    /**
     * Clears the heightmap cache (e.g. on dimension changes or world reload).
     */
    public static void clearCache() {
        for (int i = 0; i < CACHE_SIZE; i++) {
            TAGS[i] = Long.MIN_VALUE;
            HEIGHTS[i] = -1;
            TICKS[i] = -100;
        }
    }
}
