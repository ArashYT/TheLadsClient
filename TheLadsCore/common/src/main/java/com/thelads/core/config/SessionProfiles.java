package com.thelads.core.config;

import com.google.gson.JsonObject;
import com.thelads.core.client.ladsapi.minigame.Hooks;

/**
 * The Lads API game profile switch (PROTOCOL.md section 18) on the client's profile system, for the whole session only: the
 * player's own settings are snapshotted in memory, a stored profile is applied to the live config, and nothing is written to the
 * config file while it is on ({@link ConfigManager#suspendWrites}). Switching back restores the snapshot. Version-neutral.
 */
public final class SessionProfiles implements Hooks.ProfileSwitcher {
    /** What {@link #current} answers while the player's own settings are live: giving it back to {@link #switchTo} restores them. */
    public static final String OWN = "\u0001own";

    private JsonObject snapshot;
    private String active;

    @Override
    public synchronized String current() {
        return snapshot == null ? OWN : active;
    }

    @Override
    public synchronized boolean switchTo(String name) {
        if (OWN.equals(name)) {
            if (snapshot == null) return true;
            JsonObject back = snapshot;
            snapshot = null;
            active = null;
            ConfigManager.applyJson(back);
            ConfigManager.suspendWrites(false);
            return true;
        }
        ProfileManager pm = ProfileManager.get();
        String found = null;
        for (String n : pm.getProfileNames()) {
            if (n.equalsIgnoreCase(name)) {
                found = n;
                break;
            }
        }
        JsonObject profile = found == null ? null : pm.getProfile(found);
        if (profile == null) return false;
        if (snapshot == null) {
            ConfigManager.flush(); // the player's last edit reaches the file before it freezes
            snapshot = ConfigManager.toJson();
            ConfigManager.suspendWrites(true);
        }
        JsonObject copy = profile.deepCopy();
        if (copy.get("hud") instanceof JsonObject hud) ConfigManager.markOverridesApplied(hud);
        ConfigManager.applyJson(copy);
        active = found;
        return true;
    }
}
