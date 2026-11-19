package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonObject;
import java.util.Locale;

/**
 * The one hardware word the client may send (PROTOCOL.md section 9): the class of the device the player used last. It goes out
 * only when the server hello offered it (caps "input"), once after every hello, then when the device or pad class changes, at most
 * once per 5 s. Never a device name, id or serial. Game thread only.
 */
public final class InputGate {
    public static final long MIN_GAP_MS = 5_000;

    private boolean offered, pending;
    private Hooks.Device sentDevice;
    private Hooks.Pad sentPad;
    private long lastAt = Long.MIN_VALUE;

    /** A server hello arrived: remember whether it offered input; the next poll sends once. */
    public void hello(boolean offered) {
        this.offered = offered;
        pending = offered;
        sentDevice = null;
        sentPad = null;
    }

    /** Session over: nothing offered, the gap starts again. */
    public void clear() {
        offered = pending = false;
        sentDevice = null;
        sentPad = null;
    }

    /** Also forgets the rate gap (disconnect). */
    public void resetAll() {
        clear();
        lastAt = Long.MIN_VALUE;
    }

    public boolean offered() {
        return offered;
    }

    /** Pure builder: {"t":"input","device":"gamepad","pad":"playstation"} or {"t":"input","device":"kbm"}; null for UNKNOWN. */
    public static String build(Hooks.Device device, Hooks.Pad pad) {
        if (device == null || device == Hooks.Device.UNKNOWN) return null;
        JsonObject o = new JsonObject();
        o.addProperty("t", "input");
        if (device == Hooks.Device.GAMEPAD) {
            o.addProperty("device", "gamepad");
            o.addProperty("pad", (pad == null ? Hooks.Pad.GENERIC : pad).name().toLowerCase(Locale.ROOT));
        } else {
            o.addProperty("device", "kbm");
        }
        return o.toString();
    }

    /**
     * The payload to send now, or null. The version calls it when the player used another device (or on its normal tick: it only
     * returns something when the device class changed or a hello is waiting, and never twice within 5 s).
     */
    public String poll(long now) {
        if (!offered) return null;
        Hooks.InputSource src = Hooks.input;
        if (src == null) return null;
        Hooks.Device d = src.device();
        Hooks.Pad p = d == Hooks.Device.GAMEPAD ? src.pad() : null;
        String payload = build(d, p);
        if (payload == null) return null;
        boolean changed = pending || d != sentDevice || p != sentPad;
        if (!changed) return null;
        if (lastAt != Long.MIN_VALUE && now >= lastAt && now - lastAt < MIN_GAP_MS) return null;
        pending = false;
        sentDevice = d;
        sentPad = p;
        lastAt = now;
        return payload;
    }
}
