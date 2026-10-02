package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Native, opt-in replacement for thread-priority control; does not alter OS process priority. */
public final class ThreadPriorityModule extends Module {
    private final SliderOption render = priority("Render thread priority");
    private final SliderOption server = priority("Server thread priority");
    private final SliderOption workers = priority("Worker thread priority");
    private final Map<Thread, Integer> original = new WeakHashMap<>();
    private ScheduledExecutorService scheduler;

    private static SliderOption priority(String name) {
        return new SliderOption(name, 5, 1, 8, 1) {
            @Override public synchronized double getValue() { return super.getValue(); }
            @Override public synchronized void setValue(double value) { super.setValue(value); }
            @Override public synchronized void reset() { super.reset(); }
        };
    }

    public ThreadPriorityModule() {
        super("Threads", "Adjust game thread priorities. Disabling restores their original priorities. Higher values do not guarantee higher FPS.");
        addOption(render); addOption(server); addOption(workers);
    }
    @Override public synchronized void onEnable() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "Lads priority monitor"); thread.setDaemon(true); return thread;
        });
        scheduler.scheduleWithFixedDelay(this::update, 0, 1, TimeUnit.SECONDS);
    }
    private synchronized void update() {
        if (scheduler == null) return;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            String name = thread.getName();
            // 1.8.9 names its render thread "Client thread" and its chunk workers "Chunk Batcher n".
            int priority = name.equals("Render thread") || name.equals("Client thread") ? (int)render.getValue()
                : name.equals("Server thread") ? (int)server.getValue()
                : name.startsWith("Worker-") || name.startsWith("Chunk Batcher ") ? (int)workers.getValue() : 0;
            if (priority == 0 || !thread.isAlive()) continue;
            original.putIfAbsent(thread, thread.getPriority());
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Math.min(8, priority)));
        }
    }
    @Override public synchronized void onDisable() {
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        original.forEach((thread, priority) -> { if (thread.isAlive()) thread.setPriority(priority); });
        original.clear();
    }
}
