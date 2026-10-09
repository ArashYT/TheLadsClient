package com.thelads.core.v1_20_1.perf;

import com.mojang.blaze3d.platform.Window;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.v1_20_1.perf.borderless.BorderlessAccess1201;
import com.thelads.core.v1_20_1.perf.dynamicfps.DynamicFPSMod;
import com.thelads.core.v1_20_1.perf.dynamicfps.PowerState;
import com.thelads.core.v1_20_1.perf.dynamicfps.feature.volume.SmoothVolumeHandler;
import java.util.Arrays;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA probes of the performance features, off unless {@code -Dthelads.perfProbe=<list>} (or the environment variable LADS_PERF_PROBE) names them (comma separated:
 * {@code presence}, {@code dfps}, {@code borderless}). They drive the real module switches, the real window and the real
 * render loop, measure, and log {@code PERF-PROBE ...} lines; nothing is simulated. A QA window is never focused, so the
 * Dynamic FPS state it is in is UNFOCUSED (or HOVERED when the mouse is over it); the probe presses the module's own
 * "disable" key action to see the focused path.
 */
public final class PerfProbe1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-PerfProbe");
    private static final String[] PRESENCE_IDS = {
        "dynamic_fps", "borderlesswindow", "entityculling", "immediatelyfast", "clumps", "controlling", "searchables",
        "entity_model_features", "entity_texture_features", "jade", "mousetweaks", "appleskin", "sodium", "embeddium", "rubidium",
        "oculus", "iris", "modernfix", "ferritecore", "cloth-config", "cloth_config", "connector", "forge", "fabric-api", "theladscore"
    };

    private static Set<String> wanted = Set.of();
    private static long startNanos = -1;
    private static boolean presenceDone;
    private static Dfps dfps;
    private static Border border;
    private static Controls controls;

    private PerfProbe1201() {}

    public static void register() {
        String value = System.getProperty("thelads.perfProbe", System.getenv("LADS_PERF_PROBE"));
        if (value == null || value.isBlank()) return;
        wanted = Set.copyOf(Arrays.asList(value.toLowerCase().split(",")));
        LOGGER.info("PERF-PROBE enabled: {}", wanted);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    private static void tick(Minecraft client) {
        if (client.getOverlay() != null || client.getWindow() == null) return;
        long now = System.nanoTime();
        if (startNanos < 0) startNanos = now;
        if (now - startNanos < 4_000_000_000L) return; // let the title screen settle
        if (wanted.contains("presence") && !presenceDone) {
            presenceDone = true;
            presence();
        }
        if (wanted.contains("dfps")) {
            if (dfps == null) dfps = new Dfps();
            dfps.tick(client, now);
        }
        if (wanted.contains("controlling")) {
            if (controls == null) controls = new Controls();
            controls.tick(client, now);
        }
        if (wanted.contains("borderless") && (dfps == null || dfps.finished())) {
            if (border == null) border = new Border();
            border.tick(client, now);
        }
    }

    private static void presence() {
        LOGGER.info("PERF-PROBE presence onForge={} underConnector={} fabricLoaderVersion={}", ModPresence1201.onForge(), ModPresence1201.underConnector(),
            FabricLoader.getInstance().getModContainer("fabricloader").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
        for (String id : PRESENCE_IDS) {
            boolean fabric = FabricLoader.getInstance().isModLoaded(id);
            String where = ModPresence1201.where(id);
            LOGGER.info("PERF-PROBE presence {} fabricIsModLoaded={} lads={}", id, fabric, where == null ? "absent" : where);
        }
    }

    private static Module module(String name) {
        return PerfOptions1201.module(name);
    }

    // ---------------------------------------------------------------- Dynamic FPS

    private static final class Dfps {
        private int phase = -1;
        private long phaseStart;
        private long loops0, rendered0;
        private boolean failed;
        private final StringBuilder summary = new StringBuilder();
        private int unfocusedTarget = 20;

        boolean finished() {
            return phase >= 5;
        }

        void tick(Minecraft client, long now) {
            if (phase >= 5) return;
            Module module = module("DynamicFPS");
            if (module == null || DynamicFPSMod.getWindow() == null) {
                if (phase == -1) {
                    LOGGER.info("PERF-PROBE dfps SKIP engine not active (original Dynamic FPS installed, or module missing)");
                    phase = 5;
                }
                return;
            }
            if (phase == -1) {
                module.setEnabled(true);
                if (module.getOption("Mode") instanceof DropdownOption mode) mode.setIndex(0); // Aggressive: no 3 s grace
                if (module.getOption("Unfocused FPS") instanceof SliderOption slider) slider.setValue(unfocusedTarget);
                if (module.getOption("Hidden FPS") instanceof SliderOption slider) slider.setValue(10);
                begin(1, now);
                LOGGER.info("PERF-PROBE dfps setup: module enabled, Aggressive, Unfocused FPS {}, vanilla cap {}", unfocusedTarget, client.options.framerateLimit().get());
                return;
            }
            if (now - phaseStart < 1_000_000_000L) return; // settle
            if (phaseStart2 == 0) {
                phaseStart2 = now;
                loops0 = DynamicFPSMod.probeLoops;
                rendered0 = DynamicFPSMod.probeRendered;
                return;
            }
            if (now - phaseStart2 < 4_000_000_000L) return;
            double seconds = (now - phaseStart2) / 1e9;
            double rendered = (DynamicFPSMod.probeRendered - rendered0) / seconds;
            double loops = (DynamicFPSMod.probeLoops - loops0) / seconds;
            PowerState state = DynamicFPSMod.powerState();
            String line = String.format("phase=%d state=%s disabled=%s forced=%s target=%d renderedPerSec=%.1f loopsPerSec=%.1f volumeMaster=%.2f vsync=%s focused=%s hovered=%s",
                phase, state, DynamicFPSMod.isDisabled(), DynamicFPSMod.isForcingLowFPS(), DynamicFPSMod.targetFrameRate(), rendered, loops,
                SmoothVolumeHandler.volumeMultiplier(SoundSource.MASTER), DynamicFPSMod.enableVsync(),
                DynamicFPSMod.getWindow().isFocused(), DynamicFPSMod.getWindow().isHovered());
            boolean ok;
            switch (phase) {
                case 1 -> { // module on, real window state (never focused in QA)
                    int target = DynamicFPSMod.targetFrameRate();
                    ok = state != PowerState.FOCUSED && rendered <= target * 1.3 + 2 && rendered >= target * 0.5;
                    LOGGER.info("PERF-PROBE dfps {} {}", line, ok ? "PASS (throttled to the power state's rate)" : "FAIL");
                    DynamicFPSMod.toggleDisabled(); // the key binding's action
                    begin(2, now);
                }
                case 2 -> { // disabled by its key: full speed
                    ok = state == PowerState.FOCUSED && DynamicFPSMod.isDisabled() && rendered > unfocusedTarget * 1.8;
                    LOGGER.info("PERF-PROBE dfps {} {}", line, ok ? "PASS (full speed while disabled)" : "FAIL");
                    DynamicFPSMod.toggleDisabled();
                    DynamicFPSMod.toggleForceLowFPS();
                    begin(3, now);
                }
                case 3 -> { // forced low FPS: the unfocused profile
                    ok = state == PowerState.UNFOCUSED && DynamicFPSMod.isForcingLowFPS() && rendered <= unfocusedTarget * 1.3 + 2 && rendered >= unfocusedTarget * 0.5;
                    LOGGER.info("PERF-PROBE dfps {} {}", line, ok ? "PASS (forced low FPS)" : "FAIL");
                    DynamicFPSMod.toggleForceLowFPS();
                    module.setEnabled(false);
                    begin(4, now);
                }
                default -> { // module off: the engine releases the limit
                    ok = state == PowerState.FOCUSED && rendered > unfocusedTarget * 1.8;
                    LOGGER.info("PERF-PROBE dfps {} {}", line, ok ? "PASS (module off, vanilla limit)" : "FAIL");
                    module.setEnabled(true);
                    phase = 5;
                    LOGGER.info("PERF-PROBE dfps RESULT {}", failed || !ok ? "FAIL" : "PASS");
                    return;
                }
            }
            failed |= !ok;
        }

        private long phaseStart2;

        private void begin(int next, long now) {
            phase = next;
            phaseStart = now;
            phaseStart2 = 0;
        }
    }

    // ---------------------------------------------------------------- Controlling

    private static final class Controls {
        private int step;
        private long stepStart;
        private int allRows;
        private boolean failed;

        void tick(Minecraft client, long now) {
            if (step >= 5) return;
            if (!com.thelads.core.v1_20_1.embedded.EmbeddedMods1201.active("controlling")) {
                LOGGER.info("PERF-PROBE controlling SKIP original Controlling is installed, Lads stands down");
                step = 5;
                return;
            }
            if (step > 0 && now - stepStart < 1_500_000_000L) return;
            stepStart = now;
            switch (step) {
                case 0 -> {
                    client.setScreen(new net.minecraft.client.gui.screens.controls.KeyBindsScreen(client.screen, client.options));
                    step = 1;
                }
                case 1 -> {
                    var screen = client.screen;
                    boolean ok = screen instanceof com.thelads.core.v1_20_1.embedded.controlling.client.NewKeyBindsScreen;
                    LOGGER.info("PERF-PROBE controlling screen class {} {}", screen == null ? "none" : screen.getClass().getName(), ok ? "PASS (embedded Controlling replaced the vanilla key binds screen)" : "FAIL");
                    failed |= !ok;
                    if (ok) {
                        var c = (com.thelads.core.v1_20_1.embedded.controlling.client.NewKeyBindsScreen) screen;
                        allRows = c.keyList().children().size();
                        int rows = c.probeSearch("forward");
                        boolean found = rows >= 1 && rows < allRows;
                        LOGGER.info("PERF-PROBE controlling search all={} forward={} {}", allRows, rows, found ? "PASS (search narrows the list)" : "FAIL");
                        failed |= !found;
                        int none = c.probeSearch("name:zzzzzzqq");
                        LOGGER.info("PERF-PROBE controlling search nomatch rows={} {}", none, none == 0 ? "PASS" : "FAIL");
                        failed |= none != 0;
                        c.probeSearch("");
                        c.probeToggleFree();
                        int free = c.keyList().children().size();
                        LOGGER.info("PERF-PROBE controlling free keys rows={} {}", free, free > 1 ? "PASS (free keys list)" : "FAIL");
                        failed |= free <= 1;
                    }
                    step = 2;
                }
                case 2 -> {
                    client.setScreen(null);
                    step = 5;
                    LOGGER.info("PERF-PROBE controlling RESULT {}", failed ? "FAIL" : "PASS");
                }
                default -> { }
            }
        }
    }

    // ---------------------------------------------------------------- BorderlessFullscreen

    private static final class Border {
        private int step;
        private long stepStart;
        private boolean failed;

        void tick(Minecraft client, long now) {
            if (step >= 6) return;
            Module module = module("BorderlessFullscreen");
            Window window = client.getWindow();
            if (module == null || !((Object) window instanceof BorderlessAccess1201 access)) {
                LOGGER.info("PERF-PROBE borderless SKIP module or window hook missing (Borderless Window jar installed?)");
                step = 6;
                return;
            }
            if (now - stepStart < 2_500_000_000L && step > 0) return;
            stepStart = now;
            switch (step) {
                case 0 -> { // module on, windowed -> fullscreen with the module
                    log("windowed start", window, access);
                    module.setEnabled(true);
                    window.toggleFullScreen();
                    step = 1;
                }
                case 1 -> {
                    boolean ok = check("module on, F11", window, access, true);
                    failed |= !ok;
                    window.toggleFullScreen(); // back to windowed
                    step = 2;
                }
                case 2 -> {
                    log("after leaving fullscreen", window, access);
                    boolean ok = !access.lads$borderlessActive() && GLFW.glfwGetWindowAttrib(window.getWindow(), GLFW.GLFW_DECORATED) == GLFW.GLFW_TRUE
                        && !window.isFullscreen();
                    LOGGER.info("PERF-PROBE borderless windowed restore {}", ok ? "PASS (decorated window again)" : "FAIL");
                    failed |= !ok;
                    module.setEnabled(false);
                    window.toggleFullScreen(); // vanilla exclusive fullscreen
                    step = 3;
                }
                case 3 -> {
                    log("module off, F11 (exclusive)", window, access);
                    boolean exclusive = GLFW.glfwGetWindowMonitor(window.getWindow()) != 0 && !access.lads$borderlessActive();
                    LOGGER.info("PERF-PROBE borderless exclusive {}", exclusive ? "PASS (monitor-owned window with the module off)" : "FAIL");
                    failed |= !exclusive;
                    module.setEnabled(true); // switched on while already fullscreen: the controller must convert it
                    step = 4;
                }
                case 4 -> {
                    boolean ok = check("module switched on while fullscreen", window, access, true);
                    failed |= !ok;
                    module.setEnabled(false); // and back to exclusive
                    step = 5;
                }
                case 5 -> {
                    log("module switched off while borderless", window, access);
                    boolean ok = window.isFullscreen() && !access.lads$borderlessActive() && GLFW.glfwGetWindowMonitor(window.getWindow()) != 0;
                    LOGGER.info("PERF-PROBE borderless back to exclusive {}", ok ? "PASS" : "FAIL");
                    failed |= !ok;
                    window.toggleFullScreen(); // leave fullscreen, restore the QA window
                    module.setEnabled(true);
                    step = 6;
                    LOGGER.info("PERF-PROBE borderless RESULT {}", failed ? "FAIL" : "PASS");
                }
                default -> { }
            }
        }

        private boolean check(String label, Window window, BorderlessAccess1201 access, boolean expectBorderless) {
            long handle = window.getWindow();
            int[] x = {0}, y = {0}, w = {0}, h = {0};
            GLFW.glfwGetWindowPos(handle, x, y);
            GLFW.glfwGetWindowSize(handle, w, h);
            long monitor = window.findBestMonitor() == null ? 0 : window.findBestMonitor().getMonitor();
            GLFWVidMode mode = monitor == 0 ? null : GLFW.glfwGetVideoMode(monitor);
            int[] mx = {0}, my = {0};
            if (monitor != 0) GLFW.glfwGetMonitorPos(monitor, mx, my);
            boolean decorated = GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_DECORATED) == GLFW.GLFW_TRUE;
            boolean ok = mode != null && access.lads$borderlessActive() == expectBorderless && window.isFullscreen() && !decorated
                && GLFW.glfwGetWindowMonitor(handle) == 0 && x[0] == mx[0] && y[0] == my[0] && w[0] == mode.width() && h[0] == mode.height() + 1;
            LOGGER.info("PERF-PROBE borderless {}: window {}x{} at {},{} decorated={} ownedByMonitor={} monitor {}x{} at {},{} active={} {}", label, w[0], h[0], x[0], y[0],
                decorated, GLFW.glfwGetWindowMonitor(handle) != 0, mode == null ? -1 : mode.width(), mode == null ? -1 : mode.height(), mx[0], my[0],
                access.lads$borderlessActive(), ok ? "PASS (monitor rect +1 row)" : "FAIL");
            return ok;
        }

        private void log(String label, Window window, BorderlessAccess1201 access) {
            long handle = window.getWindow();
            int[] x = {0}, y = {0}, w = {0}, h = {0};
            GLFW.glfwGetWindowPos(handle, x, y);
            GLFW.glfwGetWindowSize(handle, w, h);
            LOGGER.info("PERF-PROBE borderless state [{}]: window {}x{} at {},{} fullscreenFlag={} decorated={} ownedByMonitor={} active={}", label, w[0], h[0], x[0], y[0],
                window.isFullscreen(), GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_DECORATED) == GLFW.GLFW_TRUE, GLFW.glfwGetWindowMonitor(handle) != 0,
                access.lads$borderlessActive());
        }
    }
}
