package com.thelads.core.client.ladsapi;

/** How far a LadsClientAPI server may touch an option (PROTOCOL.md "options"). */
public enum ServerControl {
    /** Forced values and limits (Bool, PlayerAction, Slider, Dropdown, Color). */
    FULL,
    /** Limits only (min/max, choices), never a forced value. */
    LIMIT_ONLY,
    /** Never: text, actions, sounds, files, URLs, Discord, anything that could leak, run or persist. */
    NEVER
}
