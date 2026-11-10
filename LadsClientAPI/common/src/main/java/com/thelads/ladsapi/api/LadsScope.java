package com.thelads.ladsapi.api;

/**
 * A named layer of rules for one player: a game mode of your server ("duel", "bedwars"), an event, a cutscene, a timed penalty.
 * While it is on, the player gets rules.yml's {@code scopes.<name>} plus the rules set here, on top of the base, world, game-mode
 * and group rules and under {@link LadsPlayer}'s own overrides. Scopes entered later win over earlier ones. Ending the scope takes
 * all of it away again, and the player's own settings return: nothing a scope sets is ever saved on the client.
 * Every call sends a fresh full policy. Call from the server's main thread.
 */
public interface LadsScope {
    /** The scope's name, lower case. */
    String name();

    LadsScope block(String module);

    LadsScope allow(String module);

    /** Forced on (ignored by the client for never-forced modules). */
    LadsScope forceOn(String module);

    /** Slider limits; NaN = no limit on that side. */
    LadsScope limit(String module, String option, double min, double max);

    /** Forced value: Boolean, Number, String (dropdown choice) or "#RRGGBB". */
    LadsScope force(String module, String option, Object value);

    /** Drops the rules set here for one module (null = all); rules.yml's rules for the scope stay. */
    LadsScope clear(String module);

    /** The scope ends by itself after this many seconds, counted from this call; 0 or less = no timer (and stops a running one). */
    LadsScope expireIn(long seconds);

    /** Ends the scope now. */
    void end();
}
