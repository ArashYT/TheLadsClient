package com.thelads.core.client.discord;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** One serial background owner for Discord IPC. Never touches Minecraft objects. */
public final class DiscordRpcService implements AutoCloseable {
    public interface Events {
        void ready();
        void disconnected(int code);
        void error(int code);
    }
    public interface Transport {
        void initialize(String applicationId, Events events);
        void callbacks();
        void update(DiscordPresence presence);
        void clear();
        void shutdown();
    }
    public record Desired(boolean enabled, String applicationId, DiscordPresence.Privacy privacy, DiscordPresence presence) {}
    /** Discord takes at most 5 activity updates per 20 seconds; changes in between are coalesced into the latest. */
    static final long UPDATE_GAP = 5_000;
    private final Supplier<Transport> factory;
    private final LongSupplier time;
    private final boolean automatic;
    private final Object lifecycle = new Object();
    private volatile Desired desired = new Desired(false, "", null, null);
    private volatile String status = "Off.";
    private volatile boolean closed;
    private ScheduledExecutorService worker;
    private Transport transport;
    private String applicationId = "";
    private boolean ready, retry;
    private long connectedAt, retryAt, nextUpdate, retryDelay = 5_000;
    private DiscordPresence lastPresence;
    private DiscordPresence.Privacy lastPrivacy;

    public DiscordRpcService(Supplier<Transport> factory) { this(factory, () -> System.nanoTime() / 1_000_000, true); }
    DiscordRpcService(Supplier<Transport> factory, LongSupplier time, boolean automatic) {
        this.factory = factory; this.time = time; this.automatic = automatic;
    }
    private static final java.util.regex.Pattern APPLICATION_ID = java.util.regex.Pattern.compile("[1-9][0-9]{16,19}");
    public static boolean validApplicationId(String id) {
        if (id == null || !APPLICATION_ID.matcher(id).matches()) return false;
        try { Long.parseUnsignedLong(id); return true; } catch (NumberFormatException invalid) { return false; }
    }
    public String status() { return status; }

    public void submit(Desired request) {
        if (closed) return;
        desired = Objects.requireNonNull(request);
        if (!request.enabled()) status = "Off.";
        else if (!validApplicationId(request.applicationId())) status = "Discord presence has no application ID.";
        synchronized (lifecycle) {
            if (!closed && automatic && worker == null && eligible(request)) {
                worker = Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "Lads Discord IPC"); thread.setDaemon(true); return thread;
                });
                worker.scheduleWithFixedDelay(this::pump, 0, 500, TimeUnit.MILLISECONDS);
            }
        }
    }
    private static boolean eligible(Desired request) {
        return request.enabled() && validApplicationId(request.applicationId()) && request.presence() != null && request.privacy() != null;
    }

    synchronized void pump() {
        if (closed) return;
        Desired request = desired;
        long now = time.getAsLong();
        if (!eligible(request)) {
            stop(); retryAt = 0; retryDelay = 5_000;
            status = !request.enabled() ? "Off." : "Discord presence has no application ID.";
            return;
        }
        if (transport != null && !applicationId.equals(request.applicationId())) {
            stop(); retryAt = 0; retryDelay = 5_000;
        }
        try {
            if (transport == null) {
                if (now < retryAt) return;
                ready = false; retry = false; applicationId = request.applicationId();
                status = "Connecting to the Discord desktop app...";
                transport = factory.get(); connectedAt = now;
                transport.initialize(applicationId, new Events() {
                    public void ready() { ready = true; retryDelay = 5_000; status = "Connected to Discord."; }
                    public void disconnected(int code) { ready = false; retry = true; status = "Discord disconnected (" + code + "). Retrying..."; }
                    public void error(int code) { ready = false; retry = true; status = "Discord rejected the request (" + code + "). Check app setup."; }
                });
            }
            transport.callbacks();
            if (closed || desired != request) return;
            if (retry || !ready && now - connectedAt >= 30_000) {
                if (!retry) status = "Waiting for Discord. Open its desktop app.";
                backOff(now); return;
            }
            if (!ready) return;
            // A privacy change clears previously shared details immediately, outside update throttling.
            if (lastPrivacy != null && !lastPrivacy.equals(request.privacy())) {
                transport.clear(); lastPresence = null; nextUpdate = 0;
            }
            if (!request.presence().equals(lastPresence) && now >= nextUpdate) {
                transport.update(request.presence());
                lastPresence = request.presence(); lastPrivacy = request.privacy(); nextUpdate = now + UPDATE_GAP;
                if (!retry) status = "Connected. Presence submitted to Discord.";
            }
        } catch (RuntimeException | LinkageError failure) {
            status = "Discord IPC unavailable. Retrying; Minecraft is unaffected.";
            backOff(now);
        } finally {
            // A slow native call may outlive close's bounded wait; clean up when it returns
            // even if shutdownNow already removed the queued cleanup task.
            if (closed) stop();
        }
    }
    private void backOff(long now) {
        stop(); retryAt = now + retryDelay; retryDelay = Math.min(60_000, retryDelay * 2);
    }
    private void stop() {
        if (transport != null) {
            try { transport.clear(); } catch (RuntimeException | LinkageError ignored) {}
            try { transport.shutdown(); } catch (RuntimeException | LinkageError ignored) {}
        }
        transport = null; ready = false; retry = false; applicationId = "";
        lastPresence = null; lastPrivacy = null; nextUpdate = 0;
    }
    @Override public void close() {
        ScheduledExecutorService current;
        synchronized (lifecycle) {
            if (closed) return;
            closed = true; current = worker;
            if (current == null) { stop(); status = "Stopped."; return; }
        }
        current.execute(() -> { synchronized (this) { stop(); status = "Stopped."; } });
        current.shutdown();
        try { if (!current.awaitTermination(1, TimeUnit.SECONDS)) current.shutdownNow(); }
        catch (InterruptedException interrupted) { current.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
