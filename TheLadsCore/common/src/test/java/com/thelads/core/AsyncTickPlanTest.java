package com.thelads.core;

import com.thelads.core.modules.AsyncModule;
import com.thelads.core.modules.AsyncTickPlan;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsyncTickPlanTest {
    record E(int id, int cx, int cz, boolean parallel) {}

    private static AsyncTickPlan<E> plan(List<E> entities) {
        return AsyncTickPlan.of(entities, E::parallel, E::cx, E::cz);
    }

    @Test void sameRegionKeepsOrderAndSequentialStaysOut() {
        var a = new E(1, 0, 0, true); var b = new E(2, 1, 1, true); var player = new E(3, 0, 0, false); var c = new E(4, 0, 1, true);
        var plan = plan(List.of(a, player, b, c));
        assertEquals(List.of(player), plan.sequential);
        assertEquals(3, plan.parallelCount());
        // chunks 0..1 x 0..1 are one region: one list, original order
        assertEquals(List.of(List.of(a, b, c)), plan.phase(0));
        for (int p = 1; p < AsyncTickPlan.PHASES; p++) assertTrue(plan.phase(p).isEmpty());
    }

    @Test void negativeChunksRoundDown() {
        var plan = plan(List.of(new E(1, -1, -1, true), new E(2, -2, -2, true), new E(3, -3, 0, true)));
        // -1 and -2 are region -1 (odd/odd: phase 3); -3 is region -2 (even), z 0 even: phase 0
        assertEquals(1, plan.phase(3).size());
        assertEquals(2, plan.phase(3).get(0).size());
        assertEquals(1, plan.phase(0).size());
    }

    /** The safety rule: two different regions of one phase never touch, they are at least 32 blocks apart. */
    @Test void regionsOfOnePhaseAreAtLeast32BlocksApart() {
        List<E> grid = new ArrayList<>();
        int id = 0;
        for (int cx = -9; cx <= 9; cx++) for (int cz = -9; cz <= 9; cz++) grid.add(new E(id++, cx, cz, true));
        var plan = plan(grid);
        int regions = 0;
        for (int p = 0; p < AsyncTickPlan.PHASES; p++) {
            List<List<E>> phase = plan.phase(p);
            regions += phase.size();
            for (int i = 0; i < phase.size(); i++) for (int j = i + 1; j < phase.size(); j++)
                for (E x : phase.get(i)) for (E y : phase.get(j))
                    assertTrue(gapBlocks(x, y) >= AsyncTickPlan.REGION_CHUNKS * 16, x + " and " + y + " tick together but are too close");
        }
        assertEquals(10 * 10, regions); // chunks -9..9 span regions -5..4 on each axis
    }

    /** Smallest block distance between the two chunks along the axis where they are furthest apart. */
    private static int gapBlocks(E a, E b) {
        return Math.max(Math.abs(a.cx - b.cx), Math.abs(a.cz - b.cz)) * 16 - 16;
    }

    @Test void threadsLeaveOneCoreForRendering() {
        var module = new AsyncModule();
        assertTrue(module.isEnabled(), "on by default (1.7.0 decision); it still falls back to normal ticking on its own");
        assertEquals(7, module.threads(8));
        assertEquals(1, module.threads(1));
        ((com.thelads.core.config.DropdownOption) module.getOption("Threads")).setIndex(3);
        assertEquals(4, module.threads(8));
        assertEquals(300, module.minEntities());
    }
}
