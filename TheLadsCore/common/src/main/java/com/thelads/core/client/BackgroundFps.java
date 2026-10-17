package com.thelads.core.client;

/**
 * Dynamic FPS's background states for a client without the 26.x engine (1.8.9): which state the window is in, and that state's frame
 * rate and volume. Mirrors 26.x DynamicFPSMod.checkForStateChanges0 and LadsBackgroundBridge (Balanced's 3 s grace) and the profile
 * defaults of its default_config.json. No Minecraft types, so it is unit tested on its own.
 */
public final class BackgroundFps {
    public enum State { FOCUSED, HOVERED, UNFOCUSED, ABANDONED, INVISIBLE }

    /** The module's Mode choices. */
    public static final int AGGRESSIVE = 0, BALANCED = 1, OFF = 2;
    /** A frame rate target of 260 is unlimited (the player's own limit); 0 suspends rendering. */
    public static final int UNLIMITED = 260, SUSPEND = 0;
    /** Balanced: an unfocused window keeps the focused profile this long (alt-tab and back costs nothing). */
    public static final long GRACE_MS = 3000;
    /** 26.x's hovered profile: an unfocused window under the mouse runs at 60 with full volume. */
    public static final int HOVERED_FPS = 60;

    /** The profiles the module's own sliders do not hold (26.x keeps them in its profile editor). */
    public static final class Profile {
        public float unfocusedVolume = 0.25f, hiddenVolume = 0f;
        public int idleFps = 10, idleMinutes = 5;

        public Profile clamp() {
            unfocusedVolume = Math.max(0f, Math.min(1f, unfocusedVolume));
            hiddenVolume = Math.max(0f, Math.min(1f, hiddenVolume));
            idleFps = Math.max(0, Math.min(UNLIMITED, idleFps));
            idleMinutes = Math.max(0, Math.min(60, idleMinutes));
            return this;
        }
    }

    private BackgroundFps() {}

    /** Aggressive and Balanced each set the Unfocused/Hidden sliders to their preset (26.x LadsBackgroundBridge.tick). */
    public static int[] preset(int mode) { return mode == AGGRESSIVE ? new int[] {15, 5} : new int[] {60, 30}; }

    /**
     * The window's state. {@code unfocusedMs}: how long the window has been unfocused (Balanced keeps it FOCUSED for the first 3 s);
     * {@code idleMs}: time since the last input, idle after {@code idleMinutes} (0 = never).
     */
    public static State state(int mode, boolean focused, boolean visible, boolean hovered, long unfocusedMs, long idleMs, int idleMinutes) {
        if (mode == OFF) return State.FOCUSED;
        if (focused) return idleMinutes > 0 && idleMs >= idleMinutes * 60_000L ? State.ABANDONED : State.FOCUSED;
        if (!visible) return State.INVISIBLE;
        if (hovered) return State.HOVERED;
        return mode == BALANCED && unfocusedMs < GRACE_MS ? State.FOCUSED : State.UNFOCUSED;
    }

    /** The state's frame rate target: SUSPEND, UNLIMITED (the player's limit) or a cap. */
    public static int target(State state, int unfocusedFps, int hiddenFps, Profile profile) {
        switch (state) {
            case HOVERED: return HOVERED_FPS;
            case UNFOCUSED: return unfocusedFps;
            case INVISIBLE: return hiddenFps;
            case ABANDONED: return profile.idleFps;
            default: return UNLIMITED;
        }
    }

    /** The frame rate limit for this frame: never above the player's own ({@code vanilla}); 260 keeps it. SUSPEND is handled by the caller. */
    public static int limit(int target, int vanilla) { return target >= UNLIMITED ? vanilla : Math.min(vanilla, target); }

    /** The state's master volume multiplier. */
    public static float volume(State state, Profile profile) {
        return state == State.UNFOCUSED ? profile.unfocusedVolume : state == State.INVISIBLE ? profile.hiddenVolume : 1f;
    }

    /** One step of 26.x's volume fade: up 1.0 and down 0.5 of full volume per second. */
    public static float fade(float value, float goal, float seconds) {
        return value < goal ? Math.min(goal, value + seconds) : Math.max(goal, value - seconds * 0.5f);
    }
}
