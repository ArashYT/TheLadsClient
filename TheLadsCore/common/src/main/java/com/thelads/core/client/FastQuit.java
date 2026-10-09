package com.thelads.core.client;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FastQuit, built in and always on: "Save and Quit to Title" returns at once. The adapters skip Minecraft's wait for the
 * integrated server and hand its thread to {@link #track}; the server finishes stopping and saving on that thread. A save is
 * done when the thread has ended: vanilla releases session.lock at the end of stopServer, and Forge's server-stopped hooks
 * (its current server, server configs) run before the thread ends.
 *
 * Until then, so that no two servers ever share a world, its session.lock or a loader's static server state:
 * anything that opens world storage (join, create, edit, delete, re-create, backup) and every new connection waits for all
 * background saves, behind a "Saving &lt;world&gt;..." screen ({@link #waitWhileSaving}); closing the game waits too, before
 * Minecraft shuts its executors down; a JVM shutdown hook covers every other exit (a crash, System.exit).
 *
 * Off with the JVM switch -Dlads.fastquit=false, and when the FastQuit or FastQuit-Forge mod is installed (that one does it).
 */
public final class FastQuit {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    /** FastQuit (Fabric) and FastQuit-Forge. */
    static final String[] MOD_IDS = {"fastquit", "fastquit_forge", "fastquitforge"};
    /** A JVM exit (crash, System.exit) waits this long for background saves; a normal close waits as long as they take. */
    static final long EXIT_HOOK_WAIT_MS = 120_000;

    /** One world whose server is still stopping. */
    public static final class Save {
        public final String name;
        final String key;
        final Thread thread;
        final long started = System.nanoTime();

        Save(String key, String name, Thread thread) {
            this.key = key;
            this.name = name;
            this.thread = thread;
        }

        public boolean done() { return !thread.isAlive(); }

        public long millis() { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }
    }

    private static final List<Save> SAVES = new CopyOnWriteArrayList<>();
    private static volatile Boolean enabled;
    private static boolean hookInstalled;

    private FastQuit() {}

    /** Off by -Dlads.fastquit=false or an installed FastQuit mod; read once, at the first quit. */
    public static boolean enabled() {
        Boolean on = enabled;
        if (on == null) {
            boolean other = otherFastQuitLoaded();
            on = !other && !"false".equalsIgnoreCase(System.getProperty("lads.fastquit"));
            enabled = on;
            if (!on) LOGGER.info("Lads FastQuit is off ({})", other ? "a FastQuit mod is installed" : "-Dlads.fastquit=false");
        }
        return on;
    }

    /**
     * An installed FastQuit or FastQuit-Forge, by reflection: Fabric Loader (which under Sinytra Connector lists the Fabric
     * mods), Forge's ModList and, before it exists (mixin plugins), its LoadingModList; 1.8.9 Forge's Loader.
     */
    public static boolean otherFastQuitLoaded() {
        for (String id : MOD_IDS) {
            if (call("net.fabricmc.loader.api.FabricLoader", "getInstance", "isModLoaded", id)
                || call("net.minecraftforge.fml.ModList", "get", "isLoaded", id)
                || call("net.minecraftforge.fml.loading.LoadingModList", "get", "getModFileById", id)
                || call("net.minecraftforge.fml.common.Loader", "instance", "isModLoaded", id)) return true;
        }
        return false;
    }

    /** {@code Class.instance().method(id)} is true or non-null; false when any of it is missing. */
    private static boolean call(String className, String instance, String method, String id) {
        try {
            Class<?> api = Class.forName(className);
            Object owner = api.getMethod(instance).invoke(null);
            if (owner == null) return false;
            Method query = api.getMethod(method, String.class);
            Object result = query.invoke(owner, id);
            return result instanceof Boolean ? (Boolean) result : result != null;
        } catch (Throwable absent) {
            return false;
        }
    }

    /** The adapter skipped Minecraft's wait for this world's server: its thread stops and saves in the background. */
    public static void track(Path worldDir, String name, Thread serverThread) {
        Save save = new Save(key(worldDir), name, serverThread);
        SAVES.add(save);
        installExitHook();
        Thread watcher = new Thread(() -> {
            boolean interrupted = false;
            while (serverThread.isAlive()) {
                try { serverThread.join(); } catch (InterruptedException e) { interrupted = true; }
            }
            SAVES.remove(save);
            LOGGER.info("Lads FastQuit: '{}' finished saving in the background ({} ms)", name, save.millis());
            if (interrupted) Thread.currentThread().interrupt();
        }, "Lads FastQuit watcher");
        watcher.setDaemon(true);
        watcher.start();
        LOGGER.info("Lads FastQuit: '{}' is saving in the background ({})", name, serverThread.getName());
    }

    /** True while this world's server is still stopping. */
    public static boolean saving(Path worldDir) {
        String key = key(worldDir);
        for (Save save : SAVES) if (!save.done() && save.key.equals(key)) return true;
        return false;
    }

    public static boolean anySaving() {
        for (Save save : SAVES) if (!save.done()) return true;
        return false;
    }

    /** The saves still running, oldest first. */
    public static List<Save> pending() {
        List<Save> list = new ArrayList<>();
        for (Save save : SAVES) if (!save.done()) list.add(save);
        return list;
    }

    /** "Saving New World... 3s" for the wait screens; empty when nothing is saving. */
    public static String status() {
        StringBuilder text = new StringBuilder();
        for (Save save : pending()) {
            if (text.length() > 0) text.append(", ");
            text.append("Saving ").append(save.name).append("... ").append(save.millis() / 1000).append('s');
        }
        return text.toString();
    }

    /**
     * Runs {@code frame} (one rendered frame of the adapter's "Saving" screen, which pumps window events) until no
     * background save is left; at most 100 frames a second. Returns the milliseconds it waited.
     */
    public static long waitWhileSaving(Runnable frame) {
        long start = System.nanoTime();
        while (anySaving()) {
            long frameStart = System.nanoTime();
            frame.run();
            long left = 10 - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - frameStart);
            if (left > 0) sleep(left);
        }
        long waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (waited > 0) LOGGER.info("Lads FastQuit: waited {} ms for background saves", waited);
        return waited;
    }

    /** Blocks without frames until every background save ended or the timeout passed; true when all ended. */
    public static boolean awaitAll(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        for (Save save : pending()) {
            if (save.thread == Thread.currentThread()) continue; // a stopping server never waits for itself
            long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (left <= 0) return !anySaving();
            try { save.thread.join(left); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return !anySaving(); }
        }
        return !anySaving();
    }

    /** Same folder, whatever the spelling: absolute, normalized, case-insensitive (Windows). */
    static String key(Path worldDir) {
        return worldDir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private static synchronized void installExitHook() {
        if (hookInstalled) return;
        hookInstalled = true;
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!anySaving()) return;
                // System.out too: the logging system may already be shutting down with the JVM.
                String waiting = "Lads FastQuit: the game is exiting during a background save; waiting for " + status();
                LOGGER.warn(waiting);
                System.out.println(waiting);
                long start = System.nanoTime();
                boolean done = awaitAll(EXIT_HOOK_WAIT_MS);
                String result = done ? "Lads FastQuit: background saves finished before the exit (" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + " ms)"
                    : "Lads FastQuit: " + status() + " did not finish within " + EXIT_HOOK_WAIT_MS / 1000 + " s";
                LOGGER.warn(result);
                System.out.println(result);
            }, "Lads FastQuit exit"));
        } catch (IllegalStateException alreadyExiting) {
            // the JVM is already shutting down: nothing can be added; the caller's own wait still applies
        }
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Tests only. */
    static void reset() {
        SAVES.clear();
        enabled = null;
    }
}
