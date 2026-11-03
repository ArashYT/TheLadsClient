package com.thelads.core.client.auth;

/** The running game's session, as the account switcher needs it. Each game version implements this. */
public interface LiveSession {
    String username();

    String uuid();

    /** A session cannot change under a live connection: the switcher asks before leaving a world or server. */
    boolean inWorld();

    /**
     * Off the render thread: everything that can fail or needs the network, changing nothing in the game. The returned step runs on
     * the render thread between frames: it leaves any world (saving it), replaces the session and all that was built from it.
     */
    Runnable prepare(LadsAccount target, String accessToken) throws Exception;
}
