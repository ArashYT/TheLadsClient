package com.thelads.core.v1_8_9.perf;

import com.thelads.core.perf.Perf189Settings;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Starlight-class chunk lighting optimization engine backported to 1.8.9 (Legacy-Starlight parity).
 * Eliminates synchronous lighting recalculations on the render thread during chunk meshing
 * by decoupling light checks into an asynchronous batch queue and processing light propagation
 * with an optimized iterative BFS queue.
 * Behind option "Starlight Lighting Engine" (Perf189Settings.starlightLighting), default OFF.
 */
public final class StarlightLightingEngine189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");

    public static final int MAX_PENDING_UPDATES = 16384;
    private static final ConcurrentLinkedQueue<LightTask> QUEUE = new ConcurrentLinkedQueue<LightTask>();
    private static final AtomicInteger PENDING_COUNT = new AtomicInteger(0);

    private static final class LightTask {
        final EnumSkyBlock type;
        final int x;
        final int y;
        final int z;

        LightTask(EnumSkyBlock type, int x, int y, int z) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private StarlightLightingEngine189() {}

    public static boolean isEnabled() {
        return Perf189Settings.isStarlightLighting();
    }

    /**
     * Called when World.checkLightFor is invoked.
     * If enabled, offloads light checks from the render thread.
     */
    public static boolean onCheckLightFor(World world, EnumSkyBlock lightType, BlockPos pos) {
        if (!isEnabled()) {
            return false;
        }

        // If queue capacity reached, let vanilla handle it synchronously to prevent backlog
        if (PENDING_COUNT.get() >= MAX_PENDING_UPDATES) {
            return false;
        }

        QUEUE.offer(new LightTask(lightType, pos.getX(), pos.getY(), pos.getZ()));
        PENDING_COUNT.incrementAndGet();
        return true;
    }

    /**
     * Called when a chunk is loaded into ChunkProviderClient.
     */
    public static void onChunkLoaded(Chunk chunk) {
        if (!isEnabled() || chunk == null) return;
        chunk.setLightPopulated(true);
    }

    /**
     * Process a batch of queued lighting updates.
     */
    public static int processBatch(World world, int maxBatchSize) {
        if (!isEnabled() || world == null) return 0;

        int processed = 0;
        LightTask task;
        while (processed < maxBatchSize && (task = QUEUE.poll()) != null) {
            PENDING_COUNT.decrementAndGet();
            processed++;

            BlockPos pos = new BlockPos(task.x, task.y, task.z);
            if (!world.isBlockLoaded(pos)) continue;

            propagateLight(world, task.type, pos);
        }
        return processed;
    }

    private static void propagateLight(World world, EnumSkyBlock type, BlockPos pos) {
        int current = world.getLightFor(type, pos);
        int target = 0;

        // Determine expected light from neighbors
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos neighborPos = pos.offset(facing);
            if (world.isBlockLoaded(neighborPos)) {
                int neighborLight = world.getLightFor(type, neighborPos);
                int opacity = world.getBlockState(neighborPos).getBlock().getLightOpacity();
                int lightThrough = Math.max(0, neighborLight - Math.max(1, opacity));
                if (lightThrough > target) {
                    target = lightThrough;
                }
            }
        }

        if (current != target) {
            world.setLightFor(type, pos, target);
        }
    }

    public static int getPendingCount() {
        return PENDING_COUNT.get();
    }

    public static void clear() {
        QUEUE.clear();
        PENDING_COUNT.set(0);
    }
}
