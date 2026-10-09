package com.thelads.core.v1_8_9.input;

import com.thelads.core.perf.FramePacer;
import com.thelads.core.perf.LatencyMeter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.GLSync;

/**
 * Built-in input-latency pacing for 1.8.9 (the Reflex AntiLag remake; it replaces the frame pacing that 1.7.3 switched off, which
 * blocked on the frame before last every frame and cost frame rate). FramePacer with a GL 3.2 fence per frame: the wait runs at
 * the top of the game loop, then the window's input is pumped again, so the ticks see clicks and keys made during the frame
 * limiter's sleep (vanilla pumps it in Display.update, before that sleep). RawMouse189 already reads the camera's motion later
 * still, right before the camera turns. Always on, no setting; kill switch -Dlads.reflex=false; no OpenGL 3.2 or any failure
 * turns it off for the session.
 *
 * <p>Buffered raw input (the Ixeris remake) does not apply to 1.8.9: LWJGL 2 registers no raw input; it takes mouse motion from
 * WM_MOUSEMOVE, which Windows coalesces into at most one message per pump however fast the mouse polls, and RawMouse189's JInput
 * mice are DirectInput devices read in one buffered call per frame.
 */
public final class InputLatency189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    static final FramePacer PACER = new FramePacer();
    private static boolean reflex = !"false".equalsIgnoreCase(System.getProperty("lads.reflex")), checked;
    private static GLSync previous, older;
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
        if (qaMeter == null) qaMeter = new LatencyMeter(() -> GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0),
            fence -> done((GLSync) fence), fence -> GL32.glDeleteSync((GLSync) fence));
        return qaMeter;
    }

    /** Head of Minecraft.runGameLoop. */
    public static void frameStart() {
        if (qaMeter != null) qaMeter.poll();
        pace();
        if (qaMeter != null) qaMeter.input();
    }

    private static void pace() {
        if (!active()) return;
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
            if (Display.isCreated()) Display.processMessages();
        } catch (Throwable t) {
            stop(t);
        }
    }

    /** Head of Minecraft.updateDisplay, right before the swap: the feedback, then this frame's fence. */
    public static void beforeSwap() {
        if (qaMeter != null) qaMeter.fence();
        if (!active() || !checked) return;
        try {
            PACER.frameEnd(done(previous));
            if (older != null) GL32.glDeleteSync(older);
            older = previous;
            previous = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        } catch (Throwable t) {
            stop(t);
        }
    }

    /** End of Minecraft.updateDisplay (QA only). */
    public static void afterSwap() {
        if (qaMeter != null) qaMeter.poll();
    }

    private static boolean done(GLSync fence) {
        return fence == null || GL32.glClientWaitSync(fence, 0, 0L) != GL32.GL_TIMEOUT_EXPIRED; // signalled, or failed: never wait on it
    }

    private static void stop(Throwable t) {
        reflex = false;
        try {
            if (older != null) GL32.glDeleteSync(older);
            if (previous != null) GL32.glDeleteSync(previous);
        } catch (Throwable ignored) {}
        older = previous = null;
        LOGGER.warn("Input-latency pacing failed; vanilla frame timing from now: " + t);
    }
}
