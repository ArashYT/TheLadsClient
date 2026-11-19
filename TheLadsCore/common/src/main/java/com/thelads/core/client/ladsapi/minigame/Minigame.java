package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonObject;
import com.thelads.core.client.ladsapi.LadsApiClient;

/**
 * Holder of the minigame state (PROTOCOL.md sections 8-21) and the one entry point for server messages. Everything is memory only,
 * dropped through {@link #clear} wherever policy and spectate state are dropped. Version hooks read the public state objects.
 * Game thread only.
 */
public final class Minigame {
    /** Server minigame messages: 40 a second, bursts of 80, separate from the 8/s hello/ack limiter. */
    public static final int RATE_PER_SECOND = 40, RATE_BURST = 80;

    public static final CameraState camera = new CameraState();
    public static final FxState fx = new FxState();
    public static final LockState lock = new LockState();
    public static final WaypointState waypoints = new WaypointState();
    public static final TelegraphState telegraphs = new TelegraphState();
    public static final HudState hud = new HudState();
    public static final AudioState audio = new AudioState();
    public static final ProfileState profile = new ProfileState();
    public static final RunState run = new RunState();
    public static final GhostState ghosts = new GhostState();
    public static final ActionsState actions = new ActionsState();
    public static final DialogState dialogs = new DialogState();
    public static final InputGate input = new InputGate();

    private static double tokens = RATE_BURST;
    private static long last = 0;

    private Minigame() {}

    /** Adds the tokens earned since the last call. */
    public static void refill(long now) {
        tokens = Math.min(RATE_BURST, tokens + Math.max(0, now - last) * RATE_PER_SECOND / 1000.0);
        last = now;
    }

    public static boolean hasToken() {
        return tokens >= 1;
    }

    public static void take() {
        tokens--;
    }

    /** Whether the server message type is one of the minigame ones. */
    public static boolean isMinigame(String type) {
        return Caps.capOf(type) != null;
    }

    /** The clock all durations use (the client's own, injectable in tests through LadsApiClient). */
    public static long now() {
        return LadsApiClient.now();
    }

    /** True while the server holds this input locked. Esc, pause, chat, commands, the Lads GUI key and disconnect have no entry. */
    public static boolean locked(LockState.Lock what) {
        return lock.locked(what, now());
    }

    /**
     * A minigame message from a server that said hello. Ignored unless this build declared the cap; malformed messages are
     * dropped; never throws.
     */
    public static void receive(String type, JsonObject json, long now) {
        String cap = Caps.capOf(type);
        if (cap == null || !Caps.declared(cap)) return;
        try {
            switch (type.toLowerCase(java.util.Locale.ROOT)) {
                case "rumble" -> rumble(json);
                case "camera" -> camera.apply(json, now);
                case "fx" -> fx.apply(json, now);
                case "lock" -> lock.apply(json, now);
                case "waypoint" -> waypoints.apply(json);
                case "telegraph" -> telegraphs.apply(json, now);
                case "hud" -> hud.apply(json, now);
                case "audio" -> audio.apply(json, now);
                case "profile" -> profile.apply(json);
                case "run" -> {
                    run.apply(json, now);
                    if (!run.running()) ghosts.stopAll();
                }
                case "ghost" -> ghosts.apply(json, now);
                case "actions" -> actions.apply(json);
                case "dialog" -> dialogs.apply(json, now);
                default -> { }
            }
        } catch (RuntimeException e) {
            // a bad message never breaks the connection
        }
    }

    /** A server hello of this session: input is offered when its caps contain "input". */
    public static void hello(JsonObject json) {
        boolean offered = false;
        com.google.gson.JsonElement caps = json.get("caps");
        if (caps != null && caps.isJsonArray()) {
            for (com.google.gson.JsonElement e : caps.getAsJsonArray()) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && "input".equals(e.getAsString())) offered = true;
            }
        }
        input.hello(offered && Caps.declared(Caps.INPUT));
    }

    /** Applies timed things (audio fades, ends) and drops a run that went silent. Call every client tick. */
    public static void tick() {
        long now = now();
        if (Caps.declared(Caps.AUDIO)) audio.tick(now);
        if (run.active(now) == false && ghosts.armedCount() > 0) ghosts.stopAll();
    }

    /** Server hello, reset, server switch, disconnect: everything the server set goes, the player's own profile returns. */
    public static void clear() {
        camera.clear();
        fx.clear();
        lock.clear();
        waypoints.clear();
        telegraphs.clear();
        hud.clear();
        audio.clear();
        profile.restore();
        run.clear();
        ghosts.clear();
        actions.clear();
        dialogs.clear();
        input.clear();
        Hooks.RumbleSink r = Hooks.rumble;
        if (r != null) r.stop();
    }

    /** Disconnect: also the rate limit and the input gap. */
    public static void resetAll() {
        clear();
        input.resetAll();
        tokens = RATE_BURST;
        last = 0;
    }

    private static void rumble(JsonObject json) {
        Hooks.RumbleSink sink = Hooks.rumble;
        Hooks.InputSource src = Hooks.input;
        Double ms = Wire.num(json, "ms");
        if (sink == null || ms == null) return;
        double low = Wire.num(json, "low", 0, 1, 0), high = Wire.num(json, "high", 0, 1, 0);
        if (low <= 0 && high <= 0) {
            sink.stop();
            return;
        }
        double scale = Math.max(0, Math.min(1, sink.vibrationScale()));
        if (src == null || src.device() != Hooks.Device.GAMEPAD || scale <= 0) return;
        sink.rumble(low * scale, high * scale, (int) Math.round(Math.max(1, Math.min(2000, ms))));
    }
}
