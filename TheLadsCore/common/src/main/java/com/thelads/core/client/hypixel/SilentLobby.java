package com.thelads.core.client.hypixel;

import java.util.Locale;

/**
 * SilentLobby: Mutes lobby gadget, pet, and firework sound effects while in Hypixel lobbies.
 * Prevents audio fatigue from lobby spam while preserving minigame sounds.
 */
public final class SilentLobby {
    public static final String NAME = "Silent Lobby";

    private SilentLobby() {}

    public static boolean isEnabled() {
        return HypixelMods.isActiveOnCurrentServer() && HypixelMods.on(NAME) && HypixelMods.isInLobby();
    }

    /**
     * Returns true if the sound should be suppressed in Hypixel lobbies.
     * Allocation-free string inspection.
     */
    public static boolean shouldMuteSound(String soundName) {
        if (!isEnabled() || soundName == null) return false;
        String s = soundName.toLowerCase(Locale.ROOT);

        // Fireworks
        if (s.contains("firework")) return true;

        // Gadget explosions
        if (s.contains("explode") || s.contains("explosion")) return true;

        // Gadget / pet sounds
        if (s.contains("bat") || s.contains("cow.say") || s.contains("note.") || s.contains("note_block") || s.contains("random.orb")) return true;

        return false;
    }
}
