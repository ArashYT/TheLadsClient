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

/**
 * The 1.8.9 camera's mouse input, read as late as possible: EntityRenderer calls {@link #mouseXYChange} right before it turns the
 * camera, after Display.sync's frame-limit sleep, the ticks and the input-latency pacing (InputLatency189). Vanilla reads it in
 * Display.update at the end of the previous frame, before all three.
 *
 * <p>Raw Input: deltas summed over every JInput mouse (Windows lists virtual, HID and touchpad mice; the first one is often idle).
 * LWJGL's own deltas are used until a JInput mouse reports motion, and again when they keep moving while no JInput mouse does
 * (JInput sees no motion, or the user switched to a mouse plugged in later).
 *
 * <p>Zoom: the deltas shrink with the zoom (ZoomModule.sensitivity), the remainders carried so slow motion still turns.
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
    /** Package-private for Probe145 (QA): false reads the deltas it injected, which Mouse.poll() would replace in an unfocused window. */
    boolean pumpInput = true;
    private int lwjglOnly;
    private float carryX, carryY;

    private RawMouse189() {}

    /** Replaces Minecraft's MouseHelper once; the Raw Input module is read every frame. */
    public static void install(Minecraft mc) {
        if (mc.mouseHelper == null || mc.mouseHelper instanceof RawMouse189) return;
        RawMouse189 helper = new RawMouse189();
        if (rawInputOn()) helper.mice = findMice(); // JInput's device scan takes a moment: on the title screen, not in a world
        mc.mouseHelper = helper;
    }

    @Override
    public void mouseXYChange() {
        if (pumpInput) { // pumps the window's input and Mouse.poll()s it
            Display.processMessages();
            PylonWindow189.afterPump();
        }
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
        float scale = Zoom189.sensitivity();
        if (scale == 1) { carryX = carryY = 0; return; }
        carryX += deltaX * scale;
        carryY += deltaY * scale;
        deltaX = (int) carryX;
        deltaY = (int) carryY;
        carryX -= deltaX;
        carryY -= deltaY;
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
}
