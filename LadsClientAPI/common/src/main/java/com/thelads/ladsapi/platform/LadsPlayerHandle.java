package com.thelads.ladsapi.platform;

import java.util.UUID;

/**
 * One connected player as the platform sees them. FROZEN INTERFACE (phase 0).
 */
public interface LadsPlayerHandle {
    UUID uuid();

    String name();

    /** World/level name for per-world rules ("world", "minecraft:the_nether"); on a proxy: the backend server name. */
    String world();

    boolean hasPermission(String node);

    /** Sends one lads:api payload. Paper: adds lads:api to the player's channel set first (PROTOCOL.md 1). */
    void send(byte[] payload);

    /** Disconnects with a message (require-lads-client, min-client-version). */
    void kick(String message);
}
