package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ParallelPacksTest {
    @Test
    void runsAtMostFourAtOnceAndEveryItemOnce() {
        AtomicInteger now = new AtomicInteger(), peak = new AtomicInteger();
        ConcurrentHashMap<Integer, Integer> seen = new ConcurrentHashMap<>();
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 12; i++) items.add(i);
        ParallelPacks.run(items, i -> null, ParallelPacks.MAX_AT_ONCE, i -> {
            peak.accumulateAndGet(now.incrementAndGet(), Math::max);
            try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            seen.merge(i, 1, Integer::sum);
            now.decrementAndGet();
        });
        assertEquals(12, seen.size());
        assertTrue(seen.values().stream().allMatch(c -> c == 1));
        assertTrue(peak.get() > 1 && peak.get() <= 4, "peak " + peak.get());
    }

    @Test
    void sameKeyRunsSequentiallyInOrder() {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger inGroup = new AtomicInteger();
        List<String> items = List.of("a1", "b1", "a2", "c1", "a3");
        ParallelPacks.run(items, s -> s.substring(0, 1), 4, s -> {
            if (s.startsWith("a")) assertEquals(0, inGroup.getAndIncrement());
            order.add(s);
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (s.startsWith("a")) inGroup.decrementAndGet();
        });
        assertEquals(5, order.size());
        assertTrue(order.indexOf("a1") < order.indexOf("a2") && order.indexOf("a2") < order.indexOf("a3"));
    }

    @Test
    void aFailureIsRethrownAfterTheOthersFinish() {
        AtomicInteger finished = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> ParallelPacks.run(List.of(1, 2, 3, 4, 5), i -> null, 4, i -> {
            if (i == 2) throw new IllegalStateException("boom");
            finished.incrementAndGet();
        }));
        assertEquals(4, finished.get());
    }

    @Test
    void aggregateHoldsFinishesUntilTheLastPackAndReportsFailuresFirst() {
        List<String> calls = new ArrayList<>();
        ParallelPacks.Aggregate agg = new ParallelPacks.Aggregate(new ParallelPacks.Progress() {
            public void requestStart() { calls.add("start"); }
            public void downloadStart(long size) { calls.add("size" + size); }
            public void downloadedBytes(long bytes) { calls.add("bytes" + bytes); }
            public void requestFinished(boolean ok) { calls.add(ok ? "ok" : "fail"); }
        }, 2);
        ParallelPacks.Aggregate.PackProgress a = agg.forPack(), b = agg.forPack();
        a.requestStart(); a.downloadStart(100); a.downloadedBytes(40);
        b.requestStart(); b.downloadStart(50); b.downloadedBytes(10); b.downloadedBytes(50);
        a.requestFinished(true); a.done();
        assertFalse(calls.contains("ok"));
        b.requestFinished(false); b.done();
        assertEquals(List.of("start", "size100", "bytes0", "bytes40", "start", "size150", "bytes40", "bytes50", "bytes90", "fail", "ok"), calls);
    }
}
