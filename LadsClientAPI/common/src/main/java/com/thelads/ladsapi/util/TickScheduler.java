package com.thelads.ladsapi.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * Shared tick scheduler for server-mod platforms (Fabric, Forge).
 */
public final class TickScheduler {
    private static class ScheduledTask {
        int ticksRemaining;
        final Runnable task;

        ScheduledTask(int ticksRemaining, Runnable task) {
            this.ticksRemaining = ticksRemaining;
            this.task = task;
        }
    }

    private final Queue<ScheduledTask> scheduledTasks = new ConcurrentLinkedQueue<>();
    private final Logger logger;

    public TickScheduler(Logger logger) {
        this.logger = logger;
    }

    public void runLater(Runnable task, int ticks) {
        if (task == null) return;
        scheduledTasks.add(new ScheduledTask(Math.max(1, ticks), task));
    }

    public void tick() {
        if (scheduledTasks.isEmpty()) return;
        int size = scheduledTasks.size();
        for (int i = 0; i < size; i++) {
            ScheduledTask entry = scheduledTasks.poll();
            if (entry == null) break;
            entry.ticksRemaining--;
            if (entry.ticksRemaining <= 0) {
                try {
                    entry.task.run();
                } catch (Throwable t) {
                    if (logger != null) {
                        logger.severe("Error running scheduled task: " + t.getMessage());
                    }
                }
            } else {
                scheduledTasks.add(entry);
            }
        }
    }
}
