package com.thelads.core;

import com.thelads.core.modules.AsyncSelfCheck;
import com.thelads.core.modules.AsyncSelfCheck.Change;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsyncSelfCheckTest {
    /** Runs ticks where a parallel tick costs parallelNs and a normal one normalNs per entity (plus noise); returns the changes. */
    private static List<Change> run(AsyncSelfCheck check, int ticks, double parallelNs, double normalNs, double noise, Random random, int[] parallelTicks) {
        List<Change> changes = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            boolean parallel = check.parallel();
            if (parallel) parallelTicks[0]++;
            double ns = (parallel ? parallelNs : normalNs) * (1 + noise * (random.nextDouble() * 2 - 1));
            Change change = check.record(Math.round(ns * 2000), 2000);
            if (change != Change.NONE) changes.add(change);
        }
        return changes;
    }

    @Test void fasterParallelStaysOnAndOnlySamplesNormalTicking() {
        var check = new AsyncSelfCheck();
        int[] parallel = {0};
        assertEquals(List.of(), run(check, 20 * 60 * 20, 6, 10, 0.3, new Random(1), parallel)); // 20 minutes, noisy
        assertFalse(check.benched());
        assertTrue(parallel[0] > 20 * 60 * 20 * 0.95, "normal ticking only in short samples: " + parallel[0]);
    }

    @Test void slowerParallelIsBenchedOnceWithinSecondsAndRetriedWithBackOff() {
        var check = new AsyncSelfCheck();
        int[] parallel = {0};
        var random = new Random(2);
        assertEquals(List.of(Change.BENCHED), run(check, 400, 14, 10, 0.1, random, parallel)); // 20 s
        assertTrue(check.benched());
        assertTrue(parallel[0] < 200, "slower parallel ran " + parallel[0] + " ticks before it was benched");
        assertTrue(check.lastParallel() > check.lastNormal());
        // an hour of it still being slower: no change, back-off 5, 10, 20, 40 minutes, so only a few short tries
        parallel[0] = 0;
        assertEquals(List.of(), run(check, 72000, 14, 10, 0.1, random, parallel));
        assertEquals(48000, check.backoffTicks());
        assertTrue(parallel[0] <= 4 * 20, "parallel tries in an hour: " + parallel[0] + " ticks");
    }

    @Test void benchedComesBackWhenParallelIsClearlyFasterAgain() {
        var check = new AsyncSelfCheck();
        var random = new Random(3);
        int[] parallel = {0};
        run(check, 400, 14, 10, 0.05, random, parallel);
        assertTrue(check.benched());
        assertEquals(List.of(Change.RESUMED), run(check, 6200, 5, 10, 0.05, random, parallel)); // the 5-minute try finds it faster
        assertFalse(check.benched());
        assertEquals(6000, check.backoffTicks(), "back-off starts over");
    }

    @Test void neverFlapsWhenBothAreAboutEqual() {
        var check = new AsyncSelfCheck();
        // within the 5% / 15% margins: noise alone may bench it once, but it never comes back and goes again
        List<Change> changes = run(check, 20 * 60 * 60, 9.8, 10, 0.2, new Random(4), new int[1]);
        assertTrue(changes.size() <= 1, "changes in an hour: " + changes);
    }
}
