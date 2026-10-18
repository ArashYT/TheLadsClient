package com.thelads.core.client.cosmetics.anim;

import com.thelads.core.client.cosmetics.Emotes;
import java.util.Set;
import java.util.UUID;

/**
 * The Emotes key (the wheel) and one key per emote, all in Controls. The Emotes key is B (the emote-wheel convention). Mods in our
 * packs also default to B: Xaero's Minimap "New Waypoint" (every version) and ReplayMod "Player Overview" (1.20.1). Once per game
 * version (ConfigManager.emoteKeyClashResolved), while Emotes is still on B, those two are unbound if they are on B too; no other
 * binding is ever touched. The per-emote keys start unbound.
 */
public final class EmoteKeys {
    /** Key binding names (KeyMapping.getName / KeyBinding.getKeyDescription) that give B to the Emotes key. */
    public static final Set<String> GIVE_WAY = Set.of("gui.xaero_new_waypoint", "key.replaymod.playeroverview");
    /** The wheel key's name, and the Controls category of the per-emote keys (lang: "Lads Emotes"). */
    public static final String WHEEL = "key.theladscore.emotes", CATEGORY = "key.categories.theladscore.emotes";

    private EmoteKeys() {}

    /** An emote's own key binding name (lang: the emote's name): key.theladscore.emote.groove for lads:groove. */
    public static String keyName(String emoteId) { return "key.theladscore.emote." + emoteId.substring(emoteId.indexOf(':') + 1); }

    /** An emote's key was pressed in game: plays it, or stops it when it is the one playing. */
    public static void pressed(UUID self, String emoteId) {
        Emotes.Playing playing = Emotes.get(self);
        if (playing != null && !playing.stopping() && playing.id().equals(emoteId)) Emotes.stopLocal(self);
        else Emotes.playLocal(self, emoteId);
    }
}
