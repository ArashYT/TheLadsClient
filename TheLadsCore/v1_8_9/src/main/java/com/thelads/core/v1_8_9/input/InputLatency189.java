package com.thelads.core.v1_8_9.input;

import com.thelads.core.perf.FramePacer;
import com.thelads.core.perf.LatencyMeter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;

/**
 * Built-in input-latency pacing for 1.8.9 (the Reflex AntiLag remake; it replaces the frame pacing that 1.7.3 switched off, which
 * blocked on the frame before last every frame and cost frame rate). FramePacer with a GL 3.2 fence per frame: the wait runs at
 * the top of the game loop, then the window's input is pumped again, so the ticks see clicks and keys made during the frame
 * limiter's sleep (vanilla pumps it in Display.update, before that sleep). RawMouse189 already reads the camera's motion later
 * still, right before the camera turns. Always on, no setting; kill switch -Dlads.reflex=false; no OpenGL 3.2 or any failure
 * turns it off for the session. Above ~2,000 FPS it steps aside (1.9.12): the queue it trims is under a millisecond there, while its
 * fence and wait per frame cost ~7% of the frame rate (2,690 -> 2,884 FPS in the 1.9.12 bench); below ~1,400 FPS it paces again.
 *
 * <p>Buffered raw input (the Ixeris remake) does not apply to 1.8.9: LWJGL 2 registers no raw input; it takes mouse motion from
 * WM_MOUSEMOVE, which Windows coalesces into at most one message per pump however fast the mouse polls, and RawMouse189's JInput
 * mice are DirectInput devices read in one buffered call per frame.
 */
public final class InputLatency189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    static final FramePacer PACER = new FramePacer();
    private static boolean reflex = !"false".equalsIgnoreCase(System.getProperty("lads.reflex")), checked;
    private static Object previous, older; // GlFence189 fences
    /** Frame-to-frame time (average of the last ~16) under which pacing steps aside, and over which it comes back. */
    static final long FAST_NS = 500_000L, SLOW_NS = 700_000L;
    private static long lastFrame, interval;
    private static boolean fast;
    /** QA: Probe145 and ProbeInput189 pause the pacing for their with/without comparisons; ProbeInput189's latency meter. */
    public static boolean qaPaused;
    static LatencyMeter qaMeter;

    private InputLatency189() {}

    public static boolean active() {
        return reflex && !qaPaused;
    }

    public static FramePacer pacer() {
        return PACER;
    }

    /** QA: input-to-GPU-done measurement with its own fences (ProbeInput189). */
    public static LatencyMeter meter() {
        if (qaMeter == null) qaMeter = new LatencyMeter(InputLatency189::newFence, InputLatency189::done, InputLatency189::deleteFence);
        return qaMeter;
    }

    /** Head of Minecraft.runGameLoop. */
    public static void frameStart() {
        if (qaMeter != null) qaMeter.poll();
        track();
        pace();
        if (qaMeter != null) qaMeter.input();
    }

    private static void track() {
        long now = System.nanoTime();
        if (lastFrame != 0) {
            long frame = now - lastFrame;
            interval = interval == 0 ? frame : interval + (frame - interval) / 16;
            boolean was = fast;
            fast = fast ? interval < SLOW_NS : interval < FAST_NS;
            if (fast && !was) dropFences(); // the first paced frame after it finds no fence and does not wait
        }
        lastFrame = now;
    }

    private static void pace() {
        if (!active() || fast) return;
        try {
            if (!checked) {
                checked = true;
                if (!GLContext.getCapabilities().OpenGL32) {
                    reflex = false;
                    LOGGER.info("Lads input: no OpenGL 3.2 fences; input-latency pacing is off.");
                    return;
                }
                LOGGER.info("Lads input: input-latency pacing on.");
            }
            PACER.frameStart(() -> done(previous), () -> done(older));
            if (Display.isCreated()) {
                Display.processMessages();
                com.thelads.core.v1_8_9.feature.PylonWindow189.afterPump();
            }
        } catch (Throwable t) {
            stop(t);
        }
    }

    /** Head of Minecraft.updateDisplay, right before the swap: the feedback, then this frame's fence. */
    public static void beforeSwap() {
        if (qaMeter != null) qaMeter.fence();
        if (!active() || !checked || fast) return;
        try {
            PACER.frameEnd(done(previous));
            GlFence189.delete(older);
            older = previous;
            previous = GlFence189.fence();
        } catch (Throwable t) {
            stop(t);
        }
    }

    /** End of Minecraft.updateDisplay (QA only). */
    public static void afterSwap() {
        if (qaMeter != null) qaMeter.poll();
    }

    private static Object newFence() {
        try {
            return GlFence189.fence();
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static void deleteFence(Object fence) {
        try {
            GlFence189.delete(fence);
        } catch (Throwable ignored) {}
    }

    private static boolean done(Object fence) {
        try {
            return GlFence189.signalled(fence);
        } catch (Throwable t) {
            return true; // failed: never wait on it
        }
    }

    private static void dropFences() {
        try {
            GlFence189.delete(older);
            GlFence189.delete(previous);
        } catch (Throwable ignored) {}
        older = previous = null;
    }

    private static void stop(Throwable t) {
        reflex = false;
        dropFences();
        LOGGER.warn("Input-latency pacing failed; vanilla frame timing from now: " + t);
    }
}
