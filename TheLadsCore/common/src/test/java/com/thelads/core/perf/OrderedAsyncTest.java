package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OrderedAsyncTest {
    private static void drain(Queue<Runnable> q, int expected) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int ran = 0;
        while (ran < expected && System.nanoTime() < end) {
            Runnable r = q.poll();
            if (r == null) {
                Thread.sleep(2);
                continue;
            }
            r.run();
            ran++;
        }
        assertEquals(expected, ran);
    }

    @Test
    void resultsAreAppliedInSubmitOrderEvenWhenWorkTimesDiffer() throws Exception {
        OrderedAsync a = new OrderedAsync("t-order", null);
        Queue<Runnable> main = new ConcurrentLinkedQueue<>();
        Executor ex = main::add;
        List<Integer> out = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 50; i++) {
            final int n = i;
            a.submit(() -> {
                if (n % 7 == 0) Thread.sleep(5);
                return n;
            }, out::add, ex);
        }
        drain(main, 50);
        for (int i = 0; i < 50; i++) assertEquals(i, out.get(i));
    }

    @Test
    void invalidateMakesQueuedJobsStaleButKeepsThem() throws Exception {
        OrderedAsync a = new OrderedAsync("t-gen", null);
        Queue<Runnable> main = new ConcurrentLinkedQueue<>();
        CountDownLatch gate = new CountDownLatch(1);
        List<Integer> out = Collections.synchronizedList(new ArrayList<>());
        a.submit(() -> {
            gate.await();
            return 1;
        }, out::add, main::add);
        a.submit(() -> 2, out::add, main::add);
        a.invalidate();
        a.submit(() -> 3, out::add, main::add);
        gate.countDown();
        drain(main, 3);
        assertNull(out.get(0));
        assertNull(out.get(1));
        assertEquals(3, out.get(2));
    }

    @Test
    void cancelPendingDropsApplyCallbacks() throws Exception {
        OrderedAsync a = new OrderedAsync("t-cancel", null);
        Queue<Runnable> main = new ConcurrentLinkedQueue<>();
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger applied = new AtomicInteger();
        a.submit(() -> {
            gate.await();
            return 1;
        }, v -> applied.incrementAndGet(), main::add);
        a.cancelPending();
        a.submit(() -> 2, v -> applied.incrementAndGet(), main::add);
        gate.countDown();
        drain(main, 2);
        assertEquals(1, applied.get());
    }

    @Test
    void exceptionFallsBackToNullResultAndLogsOnce() throws Exception {
        AtomicInteger errors = new AtomicInteger();
        OrderedAsync a = new OrderedAsync("t-err", t -> errors.incrementAndGet());
        Queue<Runnable> main = new ConcurrentLinkedQueue<>();
        List<String> out = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 3; i++) {
            a.submit(() -> {
                throw new IllegalStateException("boom");
            }, v -> out.add(String.valueOf(v)), main::add);
        }
        a.submit(() -> "ok", v -> out.add(String.valueOf(v)), main::add);
        drain(main, 4);
        assertEquals(1, errors.get());
        assertEquals(java.util.Arrays.asList("null", "null", "null", "ok"), out);
    }
}
