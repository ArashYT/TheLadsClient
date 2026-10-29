package com.thelads.ladsapi.test;

import com.thelads.ladsapi.util.TickScheduler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TickSchedulerTest {

    @Test
    public void testTaskRunsAfterSpecifiedTicks() {
        TickScheduler scheduler = new TickScheduler(null);
        AtomicBoolean ran = new AtomicBoolean(false);

        scheduler.runLater(() -> ran.set(true), 3);

        scheduler.tick(); // tick 1
        assertFalse(ran.get());
        scheduler.tick(); // tick 2
        assertFalse(ran.get());
        scheduler.tick(); // tick 3
        assertTrue(ran.get());

        // Subsequent ticks do not re-run
        ran.set(false);
        scheduler.tick();
        assertFalse(ran.get());
    }

    @Test
    public void testMultipleTasksAndErrorHandling() {
        TickScheduler scheduler = new TickScheduler(null);
        AtomicInteger count = new AtomicInteger(0);

        scheduler.runLater(() -> { throw new RuntimeException("Simulated error"); }, 1);
        scheduler.runLater(count::incrementAndGet, 1);

        // tick 1: throwing task shouldn't stop other tasks from running
        assertDoesNotThrow(scheduler::tick);
        assertEquals(1, count.get());
    }
}
