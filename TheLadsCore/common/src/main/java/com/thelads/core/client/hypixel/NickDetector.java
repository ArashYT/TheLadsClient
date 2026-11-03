package com.thelads.core.client.hypixel;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.NickDetectorModule;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which tab list players are probably nicked on Hypixel, from what the server already sent: no commands, packets or API calls, and it
 * never reads who is behind a nick. Two signals (the tests of github.com/Arisings/AntiNick, src/Denicker.js; Hypixel documents neither):
 * <ul>
 * <li>the UUID's version: 1 is a nick's generated one; Mojang accounts are 4, and 2 is Hypixel's NPCs, never flagged;</li>
 * <li>the skin's textures property: its profileId is the account the skin was signed for, so another account's means a borrowed skin.
 * Not its profileName: a renamed player's can lag behind, and it may be the real name of the player behind the nick.</li>
 * </ul>
 * The marker goes through {@link com.thelads.core.client.Nicknames}, so name tags and tab lists of every version show it.
 * ponytail: a Watchdog bot listed in the tab with such a profile would be flagged too; no signal tells them apart without asking the server.
 */
public final class NickDetector {
    /** A tab list entry other than the local player's: {@code textures} is its "textures" property's value, null when it has none. */
    public record Profile(UUID id, String name, String textures) {}

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}"); // anything else is a server's fake entry
    private static final Pattern PROFILE_ID = Pattern.compile("\"profileId\"\\s*:\\s*\"([0-9a-fA-F-]{32,36})\"");
    private static final long POLL = 1_000_000_000L;
    private static final String MARKER = " \u00a7c[Nick]\u00a7r";

    private static Map<String, String> markers = Map.of();
    private static final Set<String> noticed = new HashSet<>();
    private static long version, lastPoll = Long.MIN_VALUE;

    private NickDetector() {}

    public static boolean nicked(UUID id, String name, String textures) {
        if (id == null || name == null || !NAME.matcher(name).matches() || id.version() == 2) return false;
        return id.version() == 1 || borrowedSkin(id, textures);
    }

    private static boolean borrowedSkin(UUID id, String textures) {
        if (textures == null) return false;
        try {
            Matcher owner = PROFILE_ID.matcher(new String(Base64.getDecoder().decode(textures), StandardCharsets.UTF_8));
            return owner.find() && !owner.group(1).replace("-", "").equalsIgnoreCase(id.toString().replace("-", ""));
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    /** Lower-case name to its marked name, for {@link com.thelads.core.client.Nicknames}; a new map whenever {@link #version} moves. */
    public static Map<String, String> markers() { return markers; }
    public static long version() { return version; }

    /** Each client tick (HypixelMods); looks at the tab list once a second. */
    public static void tick(LadsGameBridge game, long now) {
        if (lastPoll != Long.MIN_VALUE && now >= lastPoll && now - lastPoll < POLL) return;
        lastPoll = now;
        Map<String, String> found = new HashMap<>();
        // isEnabled: off while server rules block it. Hypixel only: elsewhere these signals mean nothing (offline-mode UUIDs, skin plugins).
        if (ModuleManager.getInstance().getModule(NickDetectorModule.NAME) instanceof NickDetectorModule module && module.isEnabled() && HypixelSafetyManager.isHypixel()) {
            for (Profile player : game.getTabProfiles()) {
                if (!nicked(player.id(), player.name(), player.textures()) || player.name().equalsIgnoreCase(game.getPlayerName())) continue;
                String key = player.name().toLowerCase(Locale.ROOT);
                found.put(key, player.name() + MARKER);
                if (module.chatNotice.get() && noticed.add(key)) game.displayChatMessage("\u00a7c[Lads] \u00a7f" + player.name() + " \u00a77is probably nicked.");
            }
        }
        noticed.retainAll(found.keySet());
        if (!found.equals(markers)) {
            markers = found;
            version++;
        }
    }
}
