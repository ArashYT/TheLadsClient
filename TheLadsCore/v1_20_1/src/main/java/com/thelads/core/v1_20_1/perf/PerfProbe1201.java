package com.thelads.core.v1_20_1.perf;

import com.mojang.blaze3d.platform.Window;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
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
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWVidMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA probes of the performance features, off unless {@code -Dthelads.perfProbe=<list>} (or the environment variable
 * LADS_PERF_PROBE) names them (comma separated: {@code presence}, {@code dfps}, {@code borderless}, {@code controlling}). They
 * drive the real module switches, the real window and the real render loop, measure, and log {@code PERF-PROBE ...} lines;
 * nothing is simulated. The Dynamic FPS probe also hides and minimises its own window for a few seconds to let the real window
 * observer see an unfocused and a minimised game (a QA window is normally focused).
 */
public final class PerfProbe1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-PerfProbe");
    private static final String[] PRESENCE_IDS = {
        "dynamic_fps", "borderlesswindow", "entityculling", "immediatelyfast", "clumps", "controlling", "searchables",
        "entity_model_features", "entity_texture_features", "jade", "mousetweaks", "appleskin", "sodium", "embeddium", "rubidium",
        "oculus", "iris", "modernfix", "ferritecore", "cloth-config", "cloth_config", "connector", "forge", "fabric-api", "theladscore"
    };

    private static Set<String> wanted = Set.of();
    private static long startNanos = -1, levelSince = -1;
    private static boolean presenceDone;
    private static Dfps dfps;
    private static Border border;
    private static Controls controls;
    private static boolean quickDone;
    private static long quickAt;
    private static int quickStep;

    private PerfProbe1201() {}

    private static String value() {
        String value = System.getProperty("thelads.perfProbe", System.getenv("LADS_PERF_PROBE"));
        return value == null ? "" : value.trim().toLowerCase();
    }

    /** True when any probe is asked for: the passive probes of the embedded mods (counters, self tests) run then. */
    public static boolean passive() {
        return !value().isEmpty() && !value().equals("false");
    }

    public static void register() {
        String value = value();
        if (value.isEmpty() || value.equals("false")) return;
        // "true" or "all" runs the scenarios that do not change the window or open screens.
        if (value.equals("true") || value.equals("all")) value = "presence";
        wanted = Set.copyOf(Arrays.asList(value.split(",")));
        LOGGER.info("PERF-PROBE enabled: {}", wanted);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    /** Probes start in a settled world (6 s after joining), or after 45 s at the title screen when no world is being opened. */
    private static boolean ready(Minecraft client, long now) {
        if (client.getOverlay() != null || client.getWindow() == null) return false;
        if (startNanos < 0) startNanos = now;
        if (client.level != null && client.screen == null) {
            if (levelSince < 0) levelSince = now;
            return now - levelSince > 6_000_000_000L;
        }
        levelSince = -1;
        return client.level == null && client.screen instanceof TitleScreen && now - startNanos > 45_000_000_000L;
    }

    private static void tick(Minecraft client) {
        long now = System.nanoTime();
        if (!ready(client, now) && !started()) return;
        if (wanted.contains("presence") && !presenceDone) {
            presenceDone = true;
            presence();
        }
        if (wanted.contains("dfps")) {
            if (dfps == null) dfps = new Dfps();
            dfps.tick(client, now);
        }
        if ((wanted.contains("threads") || wanted.contains("packets")) && (dfps == null || dfps.finished())) quick(client, now);
        if (wanted.contains("controlling") && (dfps == null || dfps.finished())) {
            if (controls == null) controls = new Controls();
            controls.tick(client, now);
        }
        if (wanted.contains("borderless") && (dfps == null || dfps.finished()) && (controls == null || controls.finished())) {
            if (border == null) border = new Border();
            border.tick(client, now);
        }
    }

    // ---------------------------------------------------------------- Threads and IgnorePacketErrors

    private static void quick(Minecraft client, long now) {
        if (quickDone) return;
        if (quickStep == 0) {
            quickAt = now;
            quickStep = 1;
            if (wanted.contains("threads")) {
                Module threads = module("Threads");
                if (threads == null) { LOGGER.info("PERF-PROBE threads SKIP module missing"); } else {
                    if (threads.getOption("Render thread priority") instanceof SliderOption slider) slider.setValue(7);
                    threads.setEnabled(false);
                    threads.setEnabled(true);
                }
            }
            if (wanted.contains("packets")) packets();
            return;
        }
        if (now - quickAt < 2_500_000_000L) return;
        if (quickStep == 1) {
            Module threads = module("Threads");
            if (wanted.contains("threads") && threads != null) {
                int priority = priorityOf("Render thread");
                LOGGER.info("PERF-PROBE threads enabled: Render thread priority={} {}", priority, priority == 7 ? "PASS (module sets the render thread priority)" : "FAIL");
                threads.setEnabled(false);
            }
            quickStep = 2;
            quickAt = now;
            return;
        }
        Module threads = module("Threads");
        if (wanted.contains("threads") && threads != null) {
            int priority = priorityOf("Render thread");
            LOGGER.info("PERF-PROBE threads disabled: Render thread priority={} {}", priority, priority == 5 ? "PASS (original priority restored)" : "FAIL");
        }
        quickDone = true;
    }

    private static int priorityOf(String name) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) if (thread.getName().equals(name)) return thread.getPriority();
        return -1;
    }

    /** IgnorePacketErrors: an undecodable clientbound play packet is dropped with the module on and still an error with it off. */
    private static void packets() {
        Module module = module("IgnorePacketErrors");
        if (module == null) { LOGGER.info("PERF-PROBE packets SKIP module missing"); return; }
        boolean wasEnabled = module.isEnabled();
        try {
            module.setEnabled(true);
            boolean guarded = decode();
            module.setEnabled(false);
            boolean vanilla = !decode();
            LOGGER.info("PERF-PROBE packets IgnorePacketErrors: on={} (packet dropped), off={} (decode error kept) {}", guarded, vanilla, guarded && vanilla ? "PASS" : "FAIL");
        } finally {
            module.setEnabled(wasEnabled);
        }
    }

    /** True when decoding a packet with an unknown id did not throw. */
    private static boolean decode() {
        io.netty.channel.embedded.EmbeddedChannel channel = new io.netty.channel.embedded.EmbeddedChannel(new net.minecraft.network.PacketDecoder(net.minecraft.network.protocol.PacketFlow.CLIENTBOUND));
        channel.attr(net.minecraft.network.Connection.ATTRIBUTE_PROTOCOL).set(net.minecraft.network.ConnectionProtocol.PLAY);
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        new net.minecraft.network.FriendlyByteBuf(buffer).writeVarInt(9999);
        try {
            channel.writeInbound(buffer);
            return channel.readInbound() == null;
        } catch (RuntimeException failure) {
            return false;
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /** Once a scenario began it runs on, whatever screen is open. */
    private static boolean started() {
        return presenceDone || dfps != null || controls != null || border != null || quickStep > 0;
    }

    private static void shot(Minecraft client, String name) {
        try {
            Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), message -> { });
        } catch (RuntimeException failure) {
            LOGGER.warn("PERF-PROBE screenshot {} failed", name, failure);
        }
    }

    private static void presence() {
        LOGGER.info("PERF-PROBE presence onForge={} underConnector={} fabricLoaderVersion={}", ModPresence1201.onForge(), ModPresence1201.underConnector(),
            FabricLoader.getInstance().getModContainer("fabricloader").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
        String title = com.thelads.core.client.discord.DiscordPresence.screen(TitleScreen.class);
        String options = com.thelads.core.client.discord.DiscordPresence.screen(net.minecraft.client.gui.screens.OptionsScreen.class);
        String pause = com.thelads.core.client.discord.DiscordPresence.screen(net.minecraft.client.gui.screens.PauseScreen.class);
        boolean labelsOk = "In the main menu".equals(title) && "In the options".equals(options) && "Paused".equals(pause);
        LOGGER.info("PERF-PROBE discord screen labels: TitleScreen='{}' OptionsScreen='{}' PauseScreen='{}' (class name here: {}) {}", title, options, pause,
            TitleScreen.class.getSimpleName(), labelsOk ? "PASS" : "FAIL");
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
        private enum Phase { MODE, TARGETS, NATURAL, DISABLED, FORCED, HIDDEN, ICONIFIED, OFF, DONE }

        private Phase phase = Phase.MODE;
        private boolean entered;
        private long phaseStart, measureStart;
        private long loops0, rendered0;
        private boolean failed;
        private double renderedNatural = 0;
        private boolean sawIconified;
        private final int unfocusedTarget = 20, hiddenTarget = 10;

        boolean finished() {
            return phase == Phase.DONE;
        }

        void tick(Minecraft client, long now) {
            if (phase == Phase.DONE) return;
            Module module = module("DynamicFPS");
            if (module == null || DynamicFPSMod.getWindow() == null) {
                if (phase == Phase.MODE) {
                    LOGGER.info("PERF-PROBE dfps SKIP engine not active (original Dynamic FPS installed, or module missing)");
                    phase = Phase.DONE;
                }
                return;
            }
            long handle = client.getWindow().getWindow();
            if (!entered) {
                enter(client, module, handle);
                entered = true;
                phaseStart = now;
                measureStart = 0;
                return;
            }
            if (phase == Phase.MODE || phase == Phase.TARGETS) {
                if (now - phaseStart > 1_500_000_000L) advance(now);
                return;
            }
            if (phase == Phase.ICONIFIED && !sawIconified && DynamicFPSMod.powerState() == PowerState.INVISIBLE && DynamicFPSMod.getWindow().isIconified()) {
                sawIconified = true;
                LOGGER.info("PERF-PROBE dfps ICONIFIED observed state={} target={} iconified={}", DynamicFPSMod.powerState(), DynamicFPSMod.targetFrameRate(), DynamicFPSMod.getWindow().isIconified());
            }
            if (now - phaseStart < 1_500_000_000L) return; // settle after the change
            if (measureStart == 0) {
                measureStart = now;
                loops0 = DynamicFPSMod.probeLoops;
                rendered0 = DynamicFPSMod.probeRendered;
                return;
            }
            if (now - measureStart < 4_000_000_000L) return;
            double seconds = (now - measureStart) / 1e9;
            double rendered = (DynamicFPSMod.probeRendered - rendered0) / seconds;
            double loops = (DynamicFPSMod.probeLoops - loops0) / seconds;
            PowerState state = DynamicFPSMod.powerState();
            String line = String.format("%s state=%s disabled=%s forced=%s target=%d renderedPerSec=%.1f loopsPerSec=%.1f volumeMaster=%.2f vsync=%s focused=%s hovered=%s",
                phase, state, DynamicFPSMod.isDisabled(), DynamicFPSMod.isForcingLowFPS(), DynamicFPSMod.targetFrameRate(), rendered, loops,
                SmoothVolumeHandler.volumeMultiplier(SoundSource.MASTER), DynamicFPSMod.enableVsync(),
                DynamicFPSMod.getWindow().isFocused(), DynamicFPSMod.getWindow().isHovered());
            boolean ok = true;
            String verdict;
            switch (phase) {
                case NATURAL -> { // module on, whatever state the real window is in
                    if (state == PowerState.FOCUSED) {
                        verdict = "INFO (the QA window has the operating system focus, so the unfocused state is exercised by the phases below)";
                    } else {
                        renderedNatural = rendered;
                        int target = DynamicFPSMod.targetFrameRate();
                        ok = rendered <= target * 1.3 + 2 && rendered >= target * 0.5;
                        verdict = ok ? "PASS (throttled to the power state's rate)" : "FAIL";
                    }
                }
                case DISABLED -> { // disabled by its key: full speed
                    ok = state == PowerState.FOCUSED && DynamicFPSMod.isDisabled() && rendered > Math.max(unfocusedTarget * 2.0, renderedNatural * 1.3);
                    verdict = ok ? "PASS (full speed while disabled)" : "FAIL";
                }
                case FORCED -> { // forced low FPS: the unfocused profile
                    ok = state == PowerState.UNFOCUSED && DynamicFPSMod.isForcingLowFPS() && rendered <= unfocusedTarget * 1.3 + 2 && rendered >= unfocusedTarget * 0.5;
                    verdict = ok ? "PASS (forced low FPS)" : "FAIL";
                }
                case HIDDEN -> { // the window really loses the focus (hidden): the observer must notice
                    ok = state == PowerState.UNFOCUSED && !DynamicFPSMod.getWindow().isFocused() && rendered <= unfocusedTarget * 1.3 + 2 && rendered >= unfocusedTarget * 0.5
                        && SmoothVolumeHandler.volumeMultiplier(SoundSource.MASTER) < 1.0f;
                    verdict = ok ? "PASS (real focus loss throttles the game and lowers the volume)" : "FAIL";
                }
                case ICONIFIED -> { // the harness restores minimised QA windows, so the state is watched for in tick(), not measured here
                    ok = sawIconified;
                    verdict = ok ? "PASS (a minimised window was seen as INVISIBLE with the Hidden FPS target)" : "INFO (the QA window guard un-minimises the window before the state could be seen)";
                    ok = true;
                }
                default -> { // module off: the engine releases the limit
                    ok = state == PowerState.FOCUSED && rendered > Math.max(unfocusedTarget * 2.0, renderedNatural * 1.3);
                    verdict = ok ? "PASS (module off, vanilla limit)" : "FAIL";
                }
            }
            LOGGER.info("PERF-PROBE dfps {} {}", line, verdict);
            failed |= !ok;
            leave(client, module, handle);
            advance(now);
        }

        private void advance(long now) {
            phase = Phase.values()[phase.ordinal() + 1];
            entered = false;
            if (phase == Phase.DONE) {
                LOGGER.info("PERF-PROBE dfps RESULT {}", failed ? "FAIL" : "PASS");
            }
        }

        private void enter(Minecraft client, Module module, long handle) {
            switch (phase) {
                case MODE -> {
                    module.setEnabled(true);
                    if (module.getOption("Mode") instanceof DropdownOption mode) mode.setIndex(0); // Aggressive: no 3 s grace
                    LOGGER.info("PERF-PROBE dfps setup: module enabled, Aggressive, vanilla cap {}", client.options.framerateLimit().get());
                }
                case TARGETS -> {
                    // After the bridge applied the mode's own defaults.
                    if (module.getOption("Unfocused FPS") instanceof SliderOption slider) slider.setValue(unfocusedTarget);
                    if (module.getOption("Hidden FPS") instanceof SliderOption slider) slider.setValue(hiddenTarget);
                    // A QA window may be hovered by the mouse: make that profile visibly slower than the 60 FPS menu limit too.
                    com.thelads.core.v1_20_1.perf.dynamicfps.config.DynamicFPSConfig.INSTANCE.get(PowerState.HOVERED).setFrameRateTarget(30);
                }
                case DISABLED -> DynamicFPSMod.toggleDisabled(); // the key binding's action
                case FORCED -> DynamicFPSMod.toggleForceLowFPS();
                case HIDDEN -> GLFW.glfwHideWindow(handle);
                case ICONIFIED -> GLFW.glfwIconifyWindow(handle);
                case OFF -> module.setEnabled(false);
                default -> { }
            }
        }

        private void leave(Minecraft client, Module module, long handle) {
            switch (phase) {
                case DISABLED -> DynamicFPSMod.toggleDisabled();
                case FORCED -> DynamicFPSMod.toggleForceLowFPS();
                case HIDDEN -> GLFW.glfwShowWindow(handle);
                case ICONIFIED -> GLFW.glfwRestoreWindow(handle);
                case OFF -> module.setEnabled(true);
                default -> { }
            }
        }
    }

    // ---------------------------------------------------------------- Controlling

    private static final class Controls {
        private int step;
        private long stepStart;
        private int allRows;
        private boolean failed;

        boolean finished() {
            return step >= 5;
        }

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
                    if (!ok) {
                        step = 3;
                        return;
                    }
                    shot(client, "perf-controlling-1-list");
                    var c = (com.thelads.core.v1_20_1.embedded.controlling.client.NewKeyBindsScreen) screen;
                    allRows = c.keyList().children().size();
                    int rows = c.probeSearch("forward");
                    boolean found = rows >= 1 && rows < allRows;
                    LOGGER.info("PERF-PROBE controlling search all={} forward={} {}", allRows, rows, found ? "PASS (search narrows the list)" : "FAIL");
                    failed |= !found;
                    step = 2;
                }
                case 2 -> {
                    var c = (com.thelads.core.v1_20_1.embedded.controlling.client.NewKeyBindsScreen) client.screen;
                    shot(client, "perf-controlling-2-search");
                    int none = c.probeSearch("name:zzzzzzqq");
                    LOGGER.info("PERF-PROBE controlling search nomatch rows={} {}", none, none == 0 ? "PASS" : "FAIL");
                    failed |= none != 0;
                    c.probeSearch("");
                    c.probeToggleFree();
                    int free = c.keyList().children().size();
                    LOGGER.info("PERF-PROBE controlling free keys rows={} {}", free, free > 1 ? "PASS (free keys list)" : "FAIL");
                    failed |= free <= 1;
                    step = 4;
                }
                case 4 -> {
                    shot(client, "perf-controlling-3-free");
                    step = 3;
                }
                case 3 -> {
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
                    shot(client, "perf-borderless-cover");
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

        /** The window is the monitor's rectangle plus one row, undecorated and not owned by the monitor: GLFW's view and the OS's view (Windows). */
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
            String os = "os=n/a";
            try {
                long hwnd = GLFWNativeWin32.glfwGetWin32Window(handle);
                if (hwnd != 0) {
                    WinDef.HWND native_ = new WinDef.HWND(new com.sun.jna.Pointer(hwnd));
                    WinDef.RECT rect = new WinDef.RECT();
                    User32.INSTANCE.GetWindowRect(native_, rect);
                    int style = User32.INSTANCE.GetWindowLong(native_, User32.GWL_STYLE);
                    boolean caption = (style & 0x00C00000) != 0, frame = (style & 0x00040000) != 0;
                    os = String.format("os rect %d,%d-%d,%d (%dx%d) caption=%s sizeframe=%s", rect.left, rect.top, rect.right, rect.bottom, rect.right - rect.left, rect.bottom - rect.top, caption, frame);
                    ok &= !caption && !frame && mode != null && rect.right - rect.left == mode.width() && rect.bottom - rect.top == mode.height() + 1;
                }
            } catch (Throwable unavailable) {
                os = "os check unavailable: " + unavailable;
            }
            LOGGER.info("PERF-PROBE borderless {}: window {}x{} at {},{} decorated={} ownedByMonitor={} monitor {}x{} at {},{} active={} {} {}", label, w[0], h[0], x[0], y[0],
                decorated, GLFW.glfwGetWindowMonitor(handle) != 0, mode == null ? -1 : mode.width(), mode == null ? -1 : mode.height(), mx[0], my[0],
                access.lads$borderlessActive(), os, ok ? "PASS (monitor rect +1 row)" : "FAIL");
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
