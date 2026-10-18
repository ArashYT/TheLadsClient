package com.thelads.core.client.cosmetics.anim;

import java.util.Set;

/**
 * The Emotes key is B (the emote-wheel convention). Mods in our packs also default to B: Xaero's Minimap "New Waypoint" (every
 * version) and ReplayMod "Player Overview" (1.20.1). Once per game version (ConfigManager.emoteKeyClashResolved), while Emotes is
 * still on B, those two are unbound if they are on B too; no other binding is ever touched.
 */
public final class EmoteKeys {
    /** Key binding names (KeyMapping.getName / KeyBinding.getKeyDescription) that give B to the Emotes key. */
    public static final Set<String> GIVE_WAY = Set.of("gui.xaero_new_waypoint", "key.replaymod.playeroverview");

    private EmoteKeys() {}
}
