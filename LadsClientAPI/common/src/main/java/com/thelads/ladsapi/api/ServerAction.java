package com.thelads.ladsapi.api;

/** A key the server offers the player (PROTOCOL.md section 20). {@code key} (options.txt name) and {@code pad} (button name) may be null. */
public record ServerAction(String id, String name, String key, String pad) {}
