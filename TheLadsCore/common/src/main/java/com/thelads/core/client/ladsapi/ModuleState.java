package com.thelads.core.client.ladsapi;

/** What the server's policy says about one module (PROTOCOL.md "modules"). */
public enum ModuleState {
    /** Not mentioned: the client's own rules (HypixelSafetyManager) decide. */
    INHERIT,
    /** Lifts the client's own safety block on this server; the player still chooses. */
    ALLOW,
    /** Off, no bypass. */
    BLOCK,
    /** Forced on (server-controllable modules only; refusable by the player). */
    ON
}
