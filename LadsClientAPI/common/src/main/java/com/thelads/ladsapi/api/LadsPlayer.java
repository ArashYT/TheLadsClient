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
}
