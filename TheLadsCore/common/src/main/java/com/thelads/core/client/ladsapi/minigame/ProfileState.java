package com.thelads.core.client.ladsapi.minigame;

import com.google.gson.JsonObject;

/**
 * Game profile (PROTOCOL.md section 18). The server only names one of the player's own saved profiles; the client never answers.
 * Nothing happens when the player turned server-driven changes off or has no profile of that name. Memory only. Game thread only.
 */
public final class ProfileState {
    private boolean switched;
    private String before;

    /** A profile message: a name switches, none (or blank) returns to the profile active before. */
    public void apply(JsonObject json) {
        String name = Wire.text(json, "name", 32);
        if (name == null || name.isBlank()) {
            restore();
            return;
        }
        Hooks.ProfileSwitcher sw = Hooks.profiles;
        if (sw == null || Hooks.refusesServerChanges.getAsBoolean()) return;
        String was = sw.current();
        if (!sw.switchTo(name.trim())) return;
        if (!switched) {
            switched = true;
            before = was; // the profile from before the first switch is the one that comes back
        }
    }

    /** Back to the profile active before the first switch (reset, server switch, disconnect, hello, empty message). */
    public void restore() {
        if (!switched) return;
        switched = false;
        Hooks.ProfileSwitcher sw = Hooks.profiles;
        String b = before;
        before = null;
        if (sw != null && b != null) sw.switchTo(b);
    }

    public boolean switched() {
        return switched;
    }
}
