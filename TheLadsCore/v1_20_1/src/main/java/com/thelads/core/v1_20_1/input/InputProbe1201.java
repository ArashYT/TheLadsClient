package com.thelads.core.v1_20_1.input;

import com.thelads.core.perf.InputLatencyQa;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.windows.User32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (harness auto-world run with LADS_VERIFY_INPUT=1): the input remakes' in-game evidence (InputLatencyQa) once the QA
 * world has run 20 s. The pacing phases, then an 8000 Hz synthetic mouse two ways: flood 1 = vanilla (buffered input and the
 * precise limiter off; a thread posts WM_MOUSEMOVE to the window, which GLFW handles one message at a time with a JNI upcall into
 * MouseHandler.onMove, inside the limiter's glfwWaitEventsTimeout too, as it does WM_INPUT), flood 2 = the buffered path (the same
 * events summed per poll, one onMove). Real raw input needs the window focused, which QA windows never are.
 */
public final class InputProbe1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final int MOUSE_HZ = 8000, WM_MOUSEMOVE = 0x0200;
    private static InputLatencyQa qa;
    private static long inWorldSince;
    private static volatile int flooding;
    private static boolean failed;

    private InputProbe1201() {}

    public static void register() {
        if (!"1".equals(System.getenv("LADS_VERIFY_INPUT"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(InputProbe1201::tick);
    }

    private static void tick(Minecraft mc) {
        if (failed || qa != null && qa.done()) return;
        if (mc.level == null || mc.player == null) { inWorldSince = 0; return; }
        long now = System.nanoTime();
        if (inWorldSince == 0) inWorldSince = now;
        if (now - inWorldSince < 20_000_000_000L) return;
        try {
            if (qa == null) {
                qa = new InputLatencyQa(new InputLatencyQa.Target() {
                    @Override public void paced(boolean on) { InputLatency1201.qaReflexPaused = !on; }
                    @Override public void gpuLoad(int units) { InputLatency1201.qaCopies = units; }
                    @Override public void flood(int mode) { setFlood(mode); }
                    @Override public void window(String action) { windowAction(mc, action); }
                    @Override public void log(String line) { LOGGER.info(line); }
                }, InputLatency1201.meter(), InputLatency1201.PACER, 1, 1, 2);
                LOGGER.info("Lads input probe: 1.20.1, limit {}, vsync {}, pacing {}, buffered raw input {}", mc.options.framerateLimit().get(),
                    mc.options.enableVsync().get(), InputLatency1201.reflex, InputLatency1201.ixeris);
            }
            qa.tick();
        } catch (Throwable t) {
            setFlood(0);
            InputLatency1201.qaCopies = 0;
            InputLatency1201.qaReflexPaused = false;
            failed = true;
            LOGGER.error("Lads input probe FAILED", t);
        }
    }

    private static int savedWidth, savedHeight;

    /** Resize and restore the window, and F11's fullscreen option on and off (Lads makes it borderless with BorderlessFullscreen). */
    private static void windowAction(Minecraft mc, String action) {
        var window = mc.getWindow();
        switch (action) {
            case "resize" -> {
                savedWidth = window.getScreenWidth();
                savedHeight = window.getScreenHeight();
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.getWindow(), 800, 480);
            }
            case "restore" -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.getWindow(), savedWidth, savedHeight);
            default -> mc.options.fullscreen().set("fullscreen".equals(action));
        }
        LOGGER.info("Lads input probe: window {} -> {}x{}, fullscreen {}", action, window.getScreenWidth(), window.getScreenHeight(), window.isFullscreen());
    }

    private static void setFlood(int mode) {
        if (mode == flooding) return;
        flooding = 0; // stops the running thread
        InputLatency1201.qaVanilla = mode == 1;
        InputLatency1201.qaFlood = mode;
        InputLatency1201.qaPending.set(0);
        if (mode == 0) return;
        long hwnd = GLFWNativeWin32.glfwGetWin32Window(Minecraft.getInstance().getWindow().getWindow());
        flooding = mode;
        Thread flood = new Thread(() -> {
            long next = System.nanoTime(), started = next, sent = 0;
            while (flooding == mode) {
                long now = System.nanoTime();
                for (; next <= now; next += 1_000_000_000L / MOUSE_HZ, sent++) {
                    if (mode == 1) User32.PostMessage(hwnd, WM_MOUSEMOVE, 0, (sent % 2 == 0 ? 200 : 201) | (200L << 16));
                    else InputLatency1201.qaPending.incrementAndGet();
                }
                LockSupport.parkNanos(200_000L);
            }
            LOGGER.info("Lads input probe: synthetic mouse (flood {}) sent {} events ({} per second)", mode, sent, Math.round(sent * 1e9 / (System.nanoTime() - started)));
        }, "Lads QA synthetic mouse");
        flood.setDaemon(true);
        flood.start();
    }
}
