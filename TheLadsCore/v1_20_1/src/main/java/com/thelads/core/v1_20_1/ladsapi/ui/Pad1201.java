package com.thelads.core.v1_20_1.ladsapi.ui;

import com.thelads.core.client.ladsapi.minigame.Hooks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The input device (cap {@code input}) of Lads API on 1.20.1, over the GLFW gamepad API the game's window library already has.
 * {@link #kbm()} is stamped by the keyboard and mouse hooks; a pad counts as used when a button or stick of it changed after that.
 * Nothing is sent to the server except the device class (xbox, playstation, switch, generic): never a name or an id. GLFW has no
 * motor API, so there is no rumble sink on this version (cap {@code rumble} is not declared).
 */
public final class Pad1201 implements Hooks.InputSource {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final Pad1201 INSTANCE = new Pad1201();
    /** Names of GLFW's gamepad buttons 0-14. */
    private static final String[] NAMES = {"a", "b", "x", "y", "lb", "rb", "?", "?", "?", "ls", "rs", "dpad_up", "dpad_right",
        "dpad_down", "dpad_left"};

    private static volatile long lastKbm, lastPad;
    /** QA seam: a synthetic pad class (0 generic, 1 xbox, 2 playstation, 3 switch, -1 = none), so the device report can be tested without hardware. */
    private static volatile int qaType = -1;

    private int jid = -1;
    private int scan;
    private int type;
    private GLFWGamepadState state;
    private final boolean[] prev = new boolean[15];
    private final float[] prevAxis = new float[6];
    private final boolean[] held = new boolean[15];
    private boolean ltHeld, rtHeld;

    private Pad1201() {}

    public static Pad1201 get() {
        return INSTANCE;
    }

    /** A key press or mouse move/click happened: the keyboard and mouse were the last device. */
    public static void kbm() {
        lastKbm = System.nanoTime();
    }

    /** GLFW is part of the window and not started yet at mod init: a GLFW call now would queue an error the game then crashes on. Only the classes are checked. */
    public static boolean init() {
        try {
            Class.forName("org.lwjgl.glfw.GLFWGamepadState");
            return true;
        } catch (Throwable t) {
            LOGGER.info("Lads API: GLFW gamepad not available ({})", t.toString());
            return false;
        }
    }

    /** Client tick: keeps a pad found and notices its use. */
    public void tick() {
        try {
            if (jid >= 0 && !GLFW.glfwJoystickIsGamepad(jid)) close();
            if (jid < 0 && ++scan % 20 == 0) open(); // looking for a pad polls the joystick drivers: once a second is enough
            if (jid < 0) return;
            if (!GLFW.glfwGetGamepadState(jid, state)) {
                close();
                return;
            }
            boolean used = false;
            for (int i = 0; i < prev.length; i++) {
                boolean down = state.buttons(i) != 0;
                if (down && !prev[i]) used = true;
                prev[i] = down;
                held[i] = down;
            }
            for (int a = 0; a < prevAxis.length; a++) {
                float v = state.axes(a);
                if (Math.abs(v - prevAxis[a]) > 0.2f && (a >= 4 ? v > -0.5f : Math.abs(v) > 0.25f)) used = true;
                prevAxis[a] = v;
            }
            ltHeld = state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) > 0.4f;
            rtHeld = state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) > 0.4f;
            if (used) lastPad = System.nanoTime();
        } catch (Throwable t) {
            jid = -1;
            LOGGER.info("Lads API: GLFW gamepad polling stopped ({})", t.toString());
        }
    }

    private void open() {
        for (int j = GLFW.GLFW_JOYSTICK_1; j <= GLFW.GLFW_JOYSTICK_LAST; j++) {
            if (GLFW.glfwJoystickPresent(j) && GLFW.glfwJoystickIsGamepad(j)) {
                jid = j;
                state = GLFWGamepadState.create();
                String n = GLFW.glfwGetGamepadName(j);
                n = n == null ? "" : n.toLowerCase(java.util.Locale.ROOT);
                type = n.contains("xbox") || n.contains("xinput") ? 1
                    : n.contains("playstation") || n.contains("dualsense") || n.contains("dualshock") || n.contains("ps4") || n.contains("ps5") ? 2
                    : n.contains("nintendo") || n.contains("switch") ? 3 : 0;
                return;
            }
        }
    }

    private void close() {
        jid = -1;
        type = 0;
        java.util.Arrays.fill(prev, false);
        java.util.Arrays.fill(held, false);
        ltHeld = rtHeld = false;
        if (state != null) state.free();
        state = null;
    }

    @Override
    public Hooks.Device device() {
        if ((jid >= 0 || qaType >= 0) && lastPad > lastKbm) return Hooks.Device.GAMEPAD;
        return lastKbm > 0 ? Hooks.Device.KBM : Hooks.Device.UNKNOWN;
    }

    @Override
    public Hooks.Pad pad() {
        return switch (qaType >= 0 ? qaType : type) {
            case 1 -> Hooks.Pad.XBOX;
            case 2 -> Hooks.Pad.PLAYSTATION;
            case 3 -> Hooks.Pad.SWITCH;
            default -> Hooks.Pad.GENERIC;
        };
    }

    /** Whether the named pad control (a b x y lb rb lt rt ls rs dpad_*) is held now on the active pad. */
    public boolean down(String name) {
        if (jid < 0 || name == null) return false;
        if (name.equals("lt")) return ltHeld;
        if (name.equals("rt")) return rtHeld;
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return held[i];
        return false;
    }

    /** QA only: type = class of the fake pad (0-3), or -1 to remove it, -2 = unchanged; press = the fake pad was used now. */
    public static String qa(int fakeType, boolean press) {
        if (fakeType != -2) qaType = fakeType;
        if (press) lastPad = System.nanoTime();
        return "fakeType=" + qaType + " realPad=" + (INSTANCE.jid >= 0);
    }
}
