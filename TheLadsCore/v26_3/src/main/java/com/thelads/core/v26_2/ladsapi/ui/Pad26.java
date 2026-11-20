package com.thelads.core.v26_2.ladsapi.ui;

import com.thelads.core.client.ladsapi.minigame.Hooks;
import java.nio.IntBuffer;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The input device (cap {@code input}) and the gamepad motors (cap {@code rumble}) of Lads API on 26.3, over the SDL gamepad API the
 * game's own window library already ships. {@link #kbm()} is stamped by the keyboard and mouse hooks; a pad counts as used when a
 * button or stick of it changed after that. Nothing is sent to the server except the device class (xbox, playstation, switch,
 * generic): never a name or an id. Rumble: {@code SDL_RumbleGamepad}, ended by SDL after the given time.
 */
public final class Pad26 implements Hooks.InputSource, Hooks.RumbleSink {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final Pad26 INSTANCE = new Pad26();
    private static final String[] NAMES = {"a", "b", "x", "y", "?", "?", "?", "ls", "rs", "lb", "rb", "dpad_up", "dpad_down",
        "dpad_left", "dpad_right"};

    private static volatile long lastKbm, lastPad;
    private static volatile boolean ready;
    /** QA seam: a synthetic pad (SDL pad type, -1 = none) so the device report and rumble path can be tested without hardware. */
    private static volatile int qaType = -1;
    private static volatile String qaRumble = "none";

    private long handle;
    private int type;
    private final boolean[] prev = new boolean[15];
    private final short[] prevAxis = new short[6];

    private Pad26() {}

    public static Pad26 get() {
        return INSTANCE;
    }

    /** A key press or mouse move/click happened: the keyboard and mouse were the last device. */
    public static void kbm() {
        lastKbm = System.nanoTime();
    }

    /** Starts the SDL gamepad subsystem; false when it cannot (then neither input pad detection nor rumble is declared). */
    public static boolean init() {
        try {
            ready = SDLInit.SDL_WasInit(SDLInit.SDL_INIT_GAMEPAD) != 0 || SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD);
        } catch (Throwable t) {
            LOGGER.info("Lads API: SDL gamepad not available ({})", t.toString());
            ready = false;
        }
        return ready;
    }

    /** Client tick: keeps a pad open and notices its use. */
    public void tick() {
        if (!ready) return;
        try {
            SDLGamepad.SDL_UpdateGamepads();
            if (handle != 0 && !SDLGamepad.SDL_GamepadConnected(handle)) close();
            if (handle == 0) open();
            if (handle == 0) return;
            boolean used = false;
            for (int i = 0; i < prev.length; i++) {
                if (NAMES[i].equals("?")) continue;
                boolean down = SDLGamepad.SDL_GetGamepadButton(handle, i);
                if (down && !prev[i]) used = true;
                prev[i] = down;
            }
            for (int a = 0; a < prevAxis.length; a++) {
                short v = SDLGamepad.SDL_GetGamepadAxis(handle, a);
                if (Math.abs(v - prevAxis[a]) > 6000 && Math.abs(v) > 8000) used = true;
                prevAxis[a] = v;
            }
            if (used) lastPad = System.nanoTime();
        } catch (Throwable t) {
            ready = false;
            LOGGER.info("Lads API: SDL gamepad polling stopped ({})", t.toString());
        }
    }

    private void open() {
        IntBuffer ids = SDLGamepad.SDL_GetGamepads();
        if (ids == null || ids.remaining() == 0) return;
        handle = SDLGamepad.SDL_OpenGamepad(ids.get(0));
        type = handle == 0 ? 0 : SDLGamepad.SDL_GetGamepadType(handle);
    }

    private void close() {
        if (handle != 0) SDLGamepad.SDL_CloseGamepad(handle);
        handle = 0;
        type = 0;
    }

    @Override
    public Hooks.Device device() {
        if ((handle != 0 || qaType >= 0) && lastPad > lastKbm) return Hooks.Device.GAMEPAD;
        return lastKbm > 0 ? Hooks.Device.KBM : Hooks.Device.UNKNOWN;
    }

    @Override
    public Hooks.Pad pad() {
        return switch (qaType >= 0 ? qaType : type) {
            case SDLGamepad.SDL_GAMEPAD_TYPE_XBOX360, SDLGamepad.SDL_GAMEPAD_TYPE_XBOXONE -> Hooks.Pad.XBOX;
            case SDLGamepad.SDL_GAMEPAD_TYPE_PS3, SDLGamepad.SDL_GAMEPAD_TYPE_PS4, SDLGamepad.SDL_GAMEPAD_TYPE_PS5 -> Hooks.Pad.PLAYSTATION;
            case SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_PRO, SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_LEFT,
                SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_RIGHT, SDLGamepad.SDL_GAMEPAD_TYPE_NINTENDO_SWITCH_JOYCON_PAIR -> Hooks.Pad.SWITCH;
            default -> Hooks.Pad.GENERIC;
        };
    }

    /** Whether the named pad control (a b x y lb rb lt rt ls rs dpad_*) is held now on the active pad. */
    public boolean down(String name) {
        if (handle == 0 || name == null) return false;
        try {
            if (name.equals("lt")) return SDLGamepad.SDL_GetGamepadAxis(handle, SDLGamepad.SDL_GAMEPAD_AXIS_LEFT_TRIGGER) > 12000;
            if (name.equals("rt")) return SDLGamepad.SDL_GetGamepadAxis(handle, SDLGamepad.SDL_GAMEPAD_AXIS_RIGHT_TRIGGER) > 12000;
            for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return prev[i];
        } catch (Throwable ignored) {
            // the pad went away between ticks
        }
        return false;
    }

    @Override
    public double vibrationScale() {
        return handle != 0 || qaType >= 0 ? 1.0 : 0.0;
    }

    @Override
    public void rumble(double low, double high, int ms) {
        if (qaType >= 0) {
            qaRumble = "low=" + low + " high=" + high + " ms=" + ms;
            return;
        }
        long h = handle;
        if (h == 0 || !ready) return;
        try {
            SDLGamepad.SDL_RumbleGamepad(h, (short) Math.round(Math.max(0, Math.min(1, low)) * 0xFFFF),
                (short) Math.round(Math.max(0, Math.min(1, high)) * 0xFFFF), Math.max(1, ms));
        } catch (Throwable ignored) {
            // a vanished pad: nothing to shake
        }
    }

    /** QA only: type = SDL gamepad type of the fake pad, or -1 to remove it; press = the fake pad was used now. */
    public static String qa(int fakeType, boolean press) {
        if (fakeType != -2) qaType = fakeType;
        if (press) lastPad = System.nanoTime();
        return "fakeType=" + qaType + " ready=" + ready + " realHandle=" + (INSTANCE.handle != 0) + " lastRumble=" + qaRumble;
    }

    @Override
    public void stop() {
        rumble(0, 0, 1);
    }
}
