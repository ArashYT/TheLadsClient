package com.thelads.ladsapi.api;

import java.util.UUID;

/**
 * A Lads Client player. Overrides set here sit on top of config.yml/rules.yml for this player only, until cleared or they leave.
 * Every change sends a fresh full policy. FROZEN INTERFACE (phase 0).
 * Note: methods must be called from the server's main thread.
 */
public interface LadsPlayer {
    UUID uuid();

    /** "1.9.13" from the client's hello. */
    String clientVersion();

    /** "26.3", "1.20.1", "1.8.9". */
    String minecraftVersion();

    /**
     * Whether the client proved itself through the Lads relay (PROTOCOL.md section 6). The client's hello alone can be sent by
     * any program: give perks to verified players only. False for clients older than 1.13.9, and for a few seconds after the join.
     */
    boolean verified();

    /**
     * Whether the client's hello named this ability in {@code caps} (PROTOCOL.md section 3). "spectate": the client holds the
     * camera on a spectate target and draws the mirrored hotbar itself, so the game plugin can send it the camera packet
     * instead of moving the spectator into the target's eyes. Said by the client, not proven: pick how to show something with
     * it, never grant anything. False for clients older than 1.13.9.
     */
    boolean can(String capability);

    void block(String module);

    void allow(String module);

    /** Forced on (ignored by the client for never-forced modules). */
    void forceOn(String module);

    /** Slider limits; NaN = no limit on that side. */
    void limit(String module, String option, double min, double max);

    /** Forced value: Boolean, Number, String (dropdown choice) or "#RRGGBB". */
    void force(String module, String option, Object value);

    /** Drops this player's API overrides for one module (null = all modules). */
    void clear(String module);

    /**
     * Enters the named scope (when not in it yet) and returns it: {@code player.scope("duel")} switches rules.yml's
     * {@code scopes.duel} on for this player; {@code player.scope("flash").block("Fullbright").expireIn(30)} is a timed rule.
     */
    LadsScope scope(String name);

    /** The scopes this player is in, oldest first. */
    java.util.List<String> scopes();

    /** Sends the current policy again. */
    void resend();

    /**
     * Tells the client it now watches through {@code target}'s eyes (PROTOCOL.md section 7), so it can hold the camera there and
     * draw its spectator HUD. The camera packet, hotbar and health still come from the game plugin as for any other client: this
     * only names what the server shows this player anyway. {@code name} is the text for the HUD (null = the entity's own name).
     * {@code canSwitch}: the client may ask for the next or previous target (Paper: LadsSpectateRequestEvent). A call that
     * changes nothing sends nothing, so it may run in a loop.
     */
    void spectate(UUID target, String name, boolean canSwitch);

    /** Spectating without a target (free flight). */
    void spectateFree(boolean canSwitch);

    /** Not spectating any more. Also right when the player was never spectating. */
    void spectateEnd();

    // ---- Minigame API (PROTOCOL.md sections 8-21). Each call is false and does nothing without the client's cap. ----

    /** The device the player uses now ("input" cap, section 9); UNKNOWN until the client reported it. */
    LadsInput.Device inputDevice();

    /** The pad family while {@link #inputDevice()} is GAMEPAD, else UNKNOWN. */
    LadsInput.Pad padType();

    default boolean usingGamepad() { return inputDevice() == LadsInput.Device.GAMEPAD; }

    /** The text for a button prompt that fits the player's device: {@code kbm} on keyboard, the pad texts on a gamepad. */
    String glyph(String kbm, String xbox, String playstation, String nintendo);

    /** Controller vibration: {@code low} heavy and {@code high} light motor 0..1, {@code ms} 1..2000. Not kept. */
    boolean rumble(double low, double high, int ms);

    /** A camera move (section 11): {@code lp.camera().spline(points).ms(4000).smooth().bars().play()}. */
    CameraBuilder camera();

    /** Shader, vignette, tint and flash (section 12); the flash plays once, the rest is kept for {@code ms}. */
    boolean screenFx(ScreenFx fx);

    boolean clearScreenFx();

    boolean flashScreen(String color, int ms);

    /** Locks inputs for {@code ms} (1..15000, section 13); the lock ends by itself. */
    boolean lockInput(InputLock lock, int ms);

    boolean unlockInput();

    Waypoints waypoints();

    Telegraphs telegraphs();

    Hud hud();

    Audio audio();

    Run run();

    /** Switches to the player's own saved profile of this name (section 18); null returns to the previous one. */
    boolean gameProfile(String name);

    /** The actions the player may bind and press (section 20), at most 8; an empty list removes them. */
    boolean actions(java.util.List<ServerAction> actions);

    /** Opens a toast, vote or modal (section 21); {@code callback} gets a button id, "dismiss" or "timeout". */
    boolean dialog(Dialog dialog, Dialog.Callback callback);

    boolean closeDialog(String id);

    /** Ends every minigame effect this server started for the player (camera, fx, lock, hud, waypoints, ...). */
    void clearMinigame();
}
