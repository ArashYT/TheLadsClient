package com.thelads.core.modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * One tick of Async: the entities that must tick on the main thread, then four phases of square regions of
 * REGION_CHUNKS x REGION_CHUNKS chunks. A region's phase is its checkerboard colour, so any two regions of one phase are
 * separated by at least one whole region (32 blocks) and may tick at the same time. Inside a region entities keep their
 * original order and tick one after another.
 */
public final class AsyncTickPlan<T> {
    public static final int REGION_CHUNKS = 2;
    public static final int PHASES = 4;
    public final List<T> sequential = new ArrayList<>();
    private final List<Map<Long, List<T>>> phases = new ArrayList<>();
    private int parallel;

    private AsyncTickPlan() {
        for (int i = 0; i < PHASES; i++) phases.add(new LinkedHashMap<>());
    }

    public static <T> AsyncTickPlan<T> of(Iterable<T> entities, Predicate<T> mayRunInParallel, ToIntFunction<T> chunkX, ToIntFunction<T> chunkZ) {
        AsyncTickPlan<T> plan = new AsyncTickPlan<>();
        for (T entity : entities) {
            if (!mayRunInParallel.test(entity)) { plan.sequential.add(entity); continue; }
            int rx = Math.floorDiv(chunkX.applyAsInt(entity), REGION_CHUNKS), rz = Math.floorDiv(chunkZ.applyAsInt(entity), REGION_CHUNKS);
            plan.phases.get(phaseOf(rx, rz)).computeIfAbsent((long) rx << 32 | (rz & 0xFFFFFFFFL), key -> new ArrayList<>()).add(entity);
            plan.parallel++;
        }
        return plan;
    }

    /** Regions of phase i, each an ordered list of its entities. */
    public List<List<T>> phase(int i) { return new ArrayList<>(phases.get(i).values()); }

    public int parallelCount() { return parallel; }

    static int phaseOf(int regionX, int regionZ) { return (regionX & 1) | (regionZ & 1) << 1; }
}
