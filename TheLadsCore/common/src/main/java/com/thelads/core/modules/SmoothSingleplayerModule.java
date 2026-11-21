package com.thelads.core.modules;

import com.thelads.core.config.Module;

/**
 * Smooth Singleplayer (default ON): Lads remake of Singleplayer Jitter Fix. While the client hosts an integrated server that is
 * not open to LAN, entity updates go out every tick and the server tick is kept just after the client tick (see perf.SmoothSingleplayer).
 * Kill switch: -Dlads.smoothsp=false.
 */
public class SmoothSingleplayerModule extends Module {
    public static final String NAME = "Smooth Singleplayer";

    public SmoothSingleplayerModule() {
        super(NAME, "Smooths entities and the world in singleplayer: entity updates every tick and the server tick kept in step with the client tick.");
        setEnabled(true);
    }
}
