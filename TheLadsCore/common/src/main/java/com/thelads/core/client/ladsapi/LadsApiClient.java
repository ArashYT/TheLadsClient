package com.thelads.core.client.ladsapi;

/**
 * The version adapters' only door into LadsClientAPI. Each adapter hands over raw lads:api payloads and connection events; this class
 * parses, validates and answers (PROTOCOL.md sections 3-4). All methods run on the game thread.
 *
 * FROZEN INTERFACE (phase 0): adapters (lanes T26, T1201, T189) call exactly these; lane CORE implements the bodies.
 */
public final class LadsApiClient {
    /** Sends one lads:api payload to the current server. Set by the version adapter at startup. */
    public interface Transport {
        void send(byte[] payload);
    }

    private static volatile Transport transport;
    private static volatile String minecraftVersion = "";

    private LadsApiClient() {}

    /** Called once by the version adapter at startup, e.g. install(bytes -> ..., "26.3"). */
    public static void install(Transport sender, String mcVersion) {
        transport = sender;
        minecraftVersion = mcVersion == null ? "" : mcVersion;
    }

    /** One lads:api payload from the server (already on the game thread). Oversized, malformed or rate-limited payloads are dropped. */
    public static void receive(byte[] payload) {
        // lane CORE
    }

    /** Disconnect, every JoinGame/Login packet, 26.3 configuration start: everything from the old server goes. */
    public static void reset() {
        // lane CORE
    }

    /** JoinGame/Login packet with the world's hashed seed: reset, then remember the seed for {@link #respawn}. */
    public static void joinGame(long hashedSeed) {
        // lane CORE
    }

    /** Respawn packet: resets only when the hashed seed differs from the last one (a proxy switch), not on death or a dimension change. */
    public static void respawn(long hashedSeed) {
        // lane CORE
    }

    static Transport transport() { return transport; }

    static String minecraftVersion() { return minecraftVersion; }
}
