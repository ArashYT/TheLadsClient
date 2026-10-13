package com.thelads.core.security;

import com.thelads.core.client.bridge.LadsGameBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Layer L3 in-game anti-injection detector: periodically scans loaded packages, active threads,
 * and ClassLoader caches for known cheat signatures (Vape, Raven, KeystrokesMod, Rise, Slinky, Breeze, etc.),
 * and checks for launcher watchdog alerts.
 *
 * Runs off the render thread every 60 s.
 * Does NOT flag CodeSource == null alone (Mixin, Forge ASM, lambdas, and reflection accessors lack CodeSource).
 * On confirmed detection: logs error, closes server connection, and shows a clear reason screen.
 */
public final class AntiInjectDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    private static final String[] CHEAT_PREFIXES = {
        "vape.", "vape/", "vape$",
        "raven.", "raven/", "raven$", "me.chazz.raven",
        "keystrokesmod.", "keystrokesmod/",
        "rise.", "rise/", "net.vantage.rise",
        "slinky.", "slinky/",
        "breeze.", "breeze/",
        "drip.", "drip/",
        "liquidbounce.", "liquidbounce/",
        "entropy.", "entropy/",
        "whiteout.", "whiteout/",
        "ghostclient."
    };

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicBoolean DETECTED = new AtomicBoolean(false);
    private static ScheduledExecutorService scheduler;

    private AntiInjectDetector() {}

    /**
     * Initializes the background 60s periodic scan off the render thread.
     */
    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Lads-AntiInject-Watchdog");
            t.setDaemon(true);
            return t;
        });

        // Initial delay 20s to allow game startup, then run every 60s
        scheduler.scheduleWithFixedDelay(AntiInjectDetector::runScan, 20, 60, TimeUnit.SECONDS);
        LOGGER.info("[AntiInject] In-game security scan initialized (L3 active).");
    }

    /**
     * Executes a single detection pass. Returns the detection reason if a cheat was found, or null if clean.
     */
    public static String scanOnce() {
        // 1. Check for launcher watchdog alert marker file
        String alertFinding = checkWatchdogAlert();
        if (alertFinding != null) return alertFinding;

        // 2. Scan defined packages
        try {
            Package[] packages = Package.getPackages();
            if (packages != null) {
                for (Package pkg : packages) {
                    if (pkg == null) continue;
                    String name = pkg.getName();
                    if (isCheatSignature(name)) {
                        return "package: " + name;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. Scan active threads and stack traces
        try {
            Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
            for (Map.Entry<Thread, StackTraceElement[]> entry : traces.entrySet()) {
                Thread thread = entry.getKey();
                if (thread != null) {
                    String threadName = thread.getName();
                    if (isCheatSignature(threadName)) {
                        return "thread: " + threadName;
                    }
                }
                StackTraceElement[] frames = entry.getValue();
                if (frames != null) {
                    for (StackTraceElement frame : frames) {
                        if (frame != null && isCheatSignature(frame.getClassName())) {
                            return "class in thread " + (thread != null ? thread.getName() : "unknown") + ": " + frame.getClassName();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 4. Scan ClassLoader caches (Forge LaunchClassLoader or standard ClassLoaders if accessible)
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl != null) {
                String clName = cl.getClass().getName();
                if (clName.contains("LaunchClassLoader")) {
                    Field cachedField = cl.getClass().getDeclaredField("cachedClasses");
                    cachedField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<String, ?> cached = (Map<String, ?>) cachedField.get(cl);
                    if (cached != null) {
                        for (String className : cached.keySet()) {
                            if (isCheatSignature(className)) {
                                return "cached class: " + className;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static void runScan() {
        try {
            String finding = scanOnce();
            if (finding != null) {
                onCheatDetected(finding);
            }
        } catch (Throwable ex) {
            LOGGER.debug("[AntiInject] Scan error: {}", ex.getMessage());
        }
    }

    public static void onCheatDetected(String finding) {
        if (!DETECTED.compareAndSet(false, true)) {
            return;
        }

        String msg = "An injected cheat was detected (" + finding + "). Disconnected from server.";
        LOGGER.error("[AntiInject] {}", msg);
        LadsGameBridge.get().disconnectWithReason("Security Violation", msg);
    }

    public static boolean isCheatSignature(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String prefix : CHEAT_PREFIXES) {
            if (lower.startsWith(prefix) || lower.contains("." + prefix) || lower.contains("/" + prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String checkWatchdogAlert() {
        String[] candidateDirs = {
            System.getenv("THELADS_DIR"),
            System.getProperty("user.dir"),
            "."
        };

        for (String dir : candidateDirs) {
            if (dir == null || dir.isEmpty()) continue;
            try {
                File alertFile = new File(dir, ".lads-antiinject-alert");
                if (alertFile.exists()) {
                    String content = Files.readString(alertFile.toPath()).trim();
                    try { alertFile.delete(); } catch (Throwable ignored) {}
                    return "Watchdog alert: " + content;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static void resetDetectionForTest() {
        DETECTED.set(false);
    }
}
