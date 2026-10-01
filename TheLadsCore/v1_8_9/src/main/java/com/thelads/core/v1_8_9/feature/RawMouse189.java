package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MouseHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.GLSync;

/**
 * The 1.8.9 camera's mouse input, read as late as possible: EntityRenderer calls {@link #mouseXYChange} right before it turns the
 * camera, after Display.sync's frame-limit sleep, the ticks and {@link #paceFrame}. Vanilla reads it in Display.update at the end
 * of the previous frame, before all three.
 *
 * <p>Raw Input: deltas summed over every JInput mouse (Windows lists virtual, HID and touchpad mice; the first one is often idle).
 * LWJGL's own deltas are used until a JInput mouse reports motion, and again when they keep moving while no JInput mouse does
 * (JInput sees no motion, or the user switched to a mouse plugged in later).
 */
public final class RawMouse189 extends MouseHelper {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    /** Frames of LWJGL-only motion before Raw Input counts as dead; shorter gaps are motion JInput reports a frame later. */
    private static final int LWJGL_ONLY_FRAMES = 5;
    /** JInput keeps 32 raw events between polls; a 1000-8000 Hz mouse sends more per frame and the rest were dropped. */
    private static final int RAW_EVENTS = 2048;

    /** Package-private for Probe145 (QA). */
    List<Controller> mice;
    boolean rawLive;
    private int lwjglOnly;
    private static GLSync previousFrame;
    static boolean pacing = true;

    private RawMouse189() {}

    /** Replaces Minecraft's MouseHelper once; the Raw Input module is read every frame. */
    public static void install(Minecraft mc) {
        if (mc.mouseHelper != null && !(mc.mouseHelper instanceof RawMouse189)) mc.mouseHelper = new RawMouse189();
    }

    @Override
    public void mouseXYChange() {
        Display.processMessages(); // pumps the window's input and Mouse.poll()s it
        int lwjglX = Mouse.getDX(), lwjglY = Mouse.getDY();
        int[] raw = rawInputOn() ? pollRaw() : null;
        boolean rawMoved = raw != null && (raw[0] != 0 || raw[1] != 0);
        lwjglOnly = rawMoved || (lwjglX == 0 && lwjglY == 0) ? 0 : lwjglOnly + 1;
        if (rawMoved) rawLive = true;
        else if (rawLive && lwjglOnly >= LWJGL_ONLY_FRAMES) {
            rawLive = false;
            LOGGER.info("Raw mouse input: no JInput mouse reports the movement; using LWJGL's mouse input until one does.");
        }
        if (rawMoved) {
            deltaX = raw[0];
            deltaY = -raw[1];
        } else if (raw != null && rawLive) {
            deltaX = deltaY = 0; // LWJGL saw it first: JInput reports the same motion next frame
        } else {
            deltaX = lwjglX;
            deltaY = lwjglY;
        }
    }

    @Override
    public void grabMouseCursor() {
        super.grabMouseCursor();
        if (rawInputOn()) pollRaw(); // motion made while a screen was open must not turn the camera
    }

    private static boolean rawInputOn() {
        Module module = ModuleManager.getInstance().getModule("RawInput");
        return module != null && module.isEnabled();
    }

    /** Summed relative motion of every JInput mouse since the last poll, or null without any. */
    private int[] pollRaw() {
        if (mice == null) mice = findMice();
        if (mice.isEmpty()) return null;
        int[] sum = new int[2];
        for (Controller mouse : mice) {
            if (!mouse.poll()) continue; // unplugged
            sum[0] += (int) mouse.getComponent(Component.Identifier.Axis.X).getPollData();
            sum[1] += (int) mouse.getComponent(Component.Identifier.Axis.Y).getPollData();
        }
        return sum;
    }

    private static List<Controller> findMice() {
        List<Controller> found = new ArrayList<>();
        try {
            for (Controller controller : ControllerEnvironment.getDefaultEnvironment().getControllers()) {
                if (controller.getType() != Controller.Type.MOUSE) continue;
                if (controller.getComponent(Component.Identifier.Axis.X) == null || controller.getComponent(Component.Identifier.Axis.Y) == null) continue;
                controller.setEventQueueSize(RAW_EVENTS);
                found.add(controller);
            }
            LOGGER.info(found.isEmpty() ? "Raw mouse input: JInput found no mouse; using the normal mouse input."
                : "Raw mouse input initialized via JInput: " + found.size() + " mice");
        } catch (Throwable t) {
            LOGGER.warn("Could not initialize raw mouse input: " + t);
        }
        return found;
    }

    /**
     * Every frame before the camera reads the mouse (RenderTickEvent START): wait until the GPU finished the frame before last, so
     * the driver queues at most one frame between this input and the screen (3 by default: up to 50 ms at 60 Hz with VSync).
     * The CPU still prepares a frame while the GPU draws the previous one, so the frame rate stays.
     */
    public static void paceFrame() {
        if (!pacing) {
            if (previousFrame != null) GL32.glDeleteSync(previousFrame);
            previousFrame = null;
            return;
        }
        try {
            if (previousFrame == null && !GLContext.getCapabilities().OpenGL32) {
                pacing = false;
                return;
            }
            GLSync frame = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            if (previousFrame != null) {
                GL32.glClientWaitSync(previousFrame, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 100_000_000L);
                GL32.glDeleteSync(previousFrame);
            }
            previousFrame = frame;
        } catch (Throwable t) {
            pacing = false;
            LOGGER.warn("Frame pacing for mouse input is off: " + t);
        }
    }
}
