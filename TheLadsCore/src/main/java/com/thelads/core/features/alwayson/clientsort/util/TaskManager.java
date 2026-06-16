/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.features.alwayson.clientsort.util;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class TaskManager {
    private final List<Task> tasks = new ArrayList<Task>();
    private final LinkedBlockingQueue<Task> pendingTasks = new LinkedBlockingQueue();

    public void schedule(int ticks, Runnable task) {
        this.pendingTasks.add(new Task(ticks, task));
    }

    public void tick() {
        this.tasks.removeIf(Task::tick);
        this.pendingTasks.drainTo(this.tasks);
    }

    private static class Task {
        int ticks;
        final Runnable task;

        public Task(int ticks, Runnable task) {
            this.ticks = ticks;
            this.task = task;
        }

        public boolean tick() {
            if (this.ticks-- <= 0) {
                this.task.run();
                return true;
            }
            return false;
        }
    }
}
