package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Native, opt-in replacement for thread-priority control; does not alter OS process priority. */
public final class ThreadPriorityModule extends Module {
    @FunctionalInterface
    public interface ApplyListener {
        void onApplied(ThreadPriorityModule module);
    }

    private static final List<ApplyListener> GLOBAL_LISTENERS = new CopyOnWriteArrayList<>();
    private final List<ApplyListener> listeners = new CopyOnWriteArrayList<>();

    private final SliderOption render = priority("Render thread priority");
    private final SliderOption server = priority("Server thread priority");
    private final SliderOption workers = priority("Worker thread priority");
    private final Map<Thread, Integer> original = new WeakHashMap<>();
    private ScheduledExecutorService scheduler;
    private volatile long applyCount;

    private SliderOption priority(String name) {
        return new SliderOption(name, 5, 1, 8, 1) {
            @Override public synchronized double getValue() { return super.getValue(); }
            @Override public synchronized void setValue(double value) {
                super.setValue(value);
                if (isEnabled()) update();
            }
            @Override public synchronized void reset() {
                super.reset();
                if (isEnabled()) update();
            }
        };
    }

    public ThreadPriorityModule() {
        super("Threads", "Adjust game thread priorities. Disabling restores their original priorities. Higher values do not guarantee higher FPS.");
        addOption(render); addOption(server); addOption(workers);
    }

    public static void addGlobalApplyListener(ApplyListener listener) { GLOBAL_LISTENERS.add(listener); }
    public static void removeGlobalApplyListener(ApplyListener listener) { GLOBAL_LISTENERS.remove(listener); }
    public void addApplyListener(ApplyListener listener) { listeners.add(listener); }
    public void removeApplyListener(ApplyListener listener) { listeners.remove(listener); }
    public long getApplyCount() { return applyCount; }

    /** Effective priority allowed by the OS and runtime for the given target priority on the given thread. */
    public static int effectivePriority(Thread thread, int targetPriority) {
        int clamped = Math.max(Thread.MIN_PRIORITY, Math.min(8, targetPriority));
        ThreadGroup g = thread.getThreadGroup();
        if (g != null && clamped > g.getMaxPriority()) {
            clamped = g.getMaxPriority();
        }
        return clamped;
    }

    /** Synchronously applies current priorities to all matching threads immediately. */
    public synchronized void apply() {
        if (isEnabled()) update();
    }

    @Override public synchronized void onEnable() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Lads priority monitor"); thread.setDaemon(true); return thread;
        });
        update();
        scheduler.scheduleWithFixedDelay(this::update, 1, 1, TimeUnit.SECONDS);
    }

    private synchronized void update() {
        if (!isEnabled()) return;
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        ThreadGroup root = group != null ? group : Thread.currentThread().getThreadGroup();
        while (root != null && root.getParent() != null) root = root.getParent();
        if (root == null) return;
        Thread[] threads = new Thread[Math.max(16, root.activeCount() * 2)];
        int count = root.enumerate(threads, true);
        while (count == threads.length) {
            threads = new Thread[threads.length * 2];
            count = root.enumerate(threads, true);
        }
        for (int i = 0; i < count; i++) {
            Thread thread = threads[i];
            if (thread == null) continue;
            String name = thread.getName();
            // 1.8.9 names its render thread "Client thread" and its chunk workers "Chunk Batcher n".
            int priority = name.equals("Render thread") || name.equals("Client thread") ? (int)render.getValue()
                : name.equals("Server thread") ? (int)server.getValue()
                : name.startsWith("Worker-") || name.startsWith("Chunk Batcher ") ? (int)workers.getValue() : 0;
            if (priority == 0 || !thread.isAlive()) continue;
            original.putIfAbsent(thread, thread.getPriority());
            thread.setPriority(effectivePriority(thread, priority));
        }
        applyCount++;
        for (ApplyListener listener : listeners) {
            try { listener.onApplied(this); } catch (Throwable ignored) {}
        }
        for (ApplyListener listener : GLOBAL_LISTENERS) {
            try { listener.onApplied(this); } catch (Throwable ignored) {}
        }
    }

    @Override public synchronized void onDisable() {
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        original.forEach((thread, priority) -> { if (thread.isAlive()) thread.setPriority(priority); });
        original.clear();
    }
}
