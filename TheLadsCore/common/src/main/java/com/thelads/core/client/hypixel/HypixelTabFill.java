package com.thelads.core.client.hypixel;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hypixel.quickplay.QuickplayCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure completion logic for Hypixel tab completion:
 * - Hypixel commands (/play, /party, /p, /friend, /f, /msg, /lobby, /l, /rejoin, etc.)
 * - /play mode IDs
 * - /lobby lobby names
 * - Subcommands and player names for party, friend, messaging commands
 */
public final class HypixelTabFill {
    private static final List<String> COMMANDS = Arrays.asList(
        "/play",
        "/party", "/p",
        "/friend", "/f",
        "/msg", "/w", "/tell", "/whisper", "/r",
        "/lobby", "/l", "/hub",
        "/rejoin",
        "/who", "/whereami", "/locraw", "/limbo",
        "/booster", "/tip",
        "/report", "/wdr",
        "/guild", "/g",
        "/shout",
        "/duel"
    );

    private static final List<String> LOBBIES = Arrays.asList(
        "main", "bedwars", "bw", "skywars", "sw", "duels", "arcade", "tnt", "megawalls", "mw",
        "blitz", "sg", "mcgo", "cops", "uhc", "smash", "wool", "ww", "pit", "skyblock", "sb",
        "housing", "murder", "mm", "buildbattle", "bb", "replay", "limbo"
    );

    private static final List<String> PARTY_SUBCOMMANDS = Arrays.asList(
        "invite", "join", "leave", "disband", "kick", "leader", "warp", "list", "settings",
        "chat", "mute", "transfer", "allinvite", "promote", "demote"
    );

    private static final Set<String> PARTY_PLAYER_SUBCOMMANDS = new LinkedHashSet<>(Arrays.asList(
        "invite", "kick", "leader", "transfer", "promote", "demote", "remove"
    ));

    private static final List<String> FRIEND_SUBCOMMANDS = Arrays.asList(
        "add", "remove", "list", "accept", "deny", "requests", "toggle", "notifications"
    );

    private static final Set<String> FRIEND_PLAYER_SUBCOMMANDS = new LinkedHashSet<>(Arrays.asList(
        "add", "remove", "accept", "deny"
    ));

    private static final List<String> GUILD_SUBCOMMANDS = Arrays.asList(
        "invite", "join", "leave", "kick", "chat", "info", "list", "online", "top", "tag",
        "motd", "discord", "promote", "demote", "mute", "unmute", "transfer"
    );

    private static final Set<String> GUILD_PLAYER_SUBCOMMANDS = new LinkedHashSet<>(Arrays.asList(
        "invite", "kick", "promote", "demote", "mute", "unmute", "transfer"
    ));

    private HypixelTabFill() {}

    /**
     * Pure function: computes candidates given the user's typed input text and known player names.
     */
    public static List<String> complete(String input, Collection<String> knownNames) {
        if (input == null || !input.startsWith("/")) {
            return Collections.emptyList();
        }

        int firstSpace = input.indexOf(' ');
        if (firstSpace == -1) {
            // Typing command name
            String prefix = input.toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String cmd : COMMANDS) {
                if (cmd.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    matches.add(cmd);
                }
            }
            Collections.sort(matches);
            return Collections.unmodifiableList(matches);
        }

        String cmd = input.substring(0, firstSpace).toLowerCase(Locale.ROOT);
        String args = input.substring(firstSpace + 1);

        switch (cmd) {
            case "/play": {
                if (args.contains(" ")) return Collections.emptyList();
                String prefix = args.toLowerCase(Locale.ROOT);
                List<String> matches = new ArrayList<>();
                for (String modeId : QuickplayCatalog.getAllModeIds()) {
                    if (modeId.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        matches.add(modeId);
                    }
                }
                Collections.sort(matches);
                return Collections.unmodifiableList(matches);
            }
            case "/lobby":
            case "/l":
            case "/hub": {
                if (args.contains(" ")) return Collections.emptyList();
                String prefix = args.toLowerCase(Locale.ROOT);
                List<String> matches = new ArrayList<>();
                for (String lobby : LOBBIES) {
                    if (lobby.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        matches.add(lobby);
                    }
                }
                Collections.sort(matches);
                return Collections.unmodifiableList(matches);
            }
            case "/msg":
            case "/w":
            case "/tell":
            case "/whisper":
            case "/duel":
            case "/report":
            case "/wdr": {
                if (args.contains(" ")) return Collections.emptyList(); // Finished player name, typing message
                return filterNames(args, knownNames);
            }
            case "/r": {
                return Collections.emptyList();
            }
            case "/party":
            case "/p": {
                int secondSpace = args.indexOf(' ');
                if (secondSpace == -1) {
                    // Could be subcommand OR player name (/p <player>)
                    String prefix = args.toLowerCase(Locale.ROOT);
                    List<String> matches = new ArrayList<>();
                    for (String sub : PARTY_SUBCOMMANDS) {
                        if (sub.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            matches.add(sub);
                        }
                    }
                    matches.addAll(filterNames(args, knownNames));
                    Collections.sort(matches);
                    return Collections.unmodifiableList(matches);
                }
                String sub = args.substring(0, secondSpace).toLowerCase(Locale.ROOT);
                String subArgs = args.substring(secondSpace + 1);
                if (subArgs.contains(" ")) return Collections.emptyList();
                if (PARTY_PLAYER_SUBCOMMANDS.contains(sub)) {
                    return filterNames(subArgs, knownNames);
                }
                return Collections.emptyList();
            }
            case "/friend":
            case "/f": {
                int secondSpace = args.indexOf(' ');
                if (secondSpace == -1) {
                    // Could be subcommand OR player name (/f <player>)
                    String prefix = args.toLowerCase(Locale.ROOT);
                    List<String> matches = new ArrayList<>();
                    for (String sub : FRIEND_SUBCOMMANDS) {
                        if (sub.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            matches.add(sub);
                        }
                    }
                    matches.addAll(filterNames(args, knownNames));
                    Collections.sort(matches);
                    return Collections.unmodifiableList(matches);
                }
                String sub = args.substring(0, secondSpace).toLowerCase(Locale.ROOT);
                String subArgs = args.substring(secondSpace + 1);
                if (subArgs.contains(" ")) return Collections.emptyList();
                if (FRIEND_PLAYER_SUBCOMMANDS.contains(sub)) {
                    return filterNames(subArgs, knownNames);
                }
                return Collections.emptyList();
            }
            case "/guild":
            case "/g": {
                int secondSpace = args.indexOf(' ');
                if (secondSpace == -1) {
                    String prefix = args.toLowerCase(Locale.ROOT);
                    List<String> matches = new ArrayList<>();
                    for (String sub : GUILD_SUBCOMMANDS) {
                        if (sub.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            matches.add(sub);
                        }
                    }
                    Collections.sort(matches);
                    return Collections.unmodifiableList(matches);
                }
                String sub = args.substring(0, secondSpace).toLowerCase(Locale.ROOT);
                String subArgs = args.substring(secondSpace + 1);
                if (subArgs.contains(" ")) return Collections.emptyList();
                if (GUILD_PLAYER_SUBCOMMANDS.contains(sub)) {
                    return filterNames(subArgs, knownNames);
                }
                return Collections.emptyList();
            }
            default:
                return Collections.emptyList();
        }
    }

    private static List<String> filterNames(String prefix, Collection<String> names) {
        if (names == null || names.isEmpty()) return Collections.emptyList();
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String name : names) {
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(p)) {
                if (!matches.contains(name)) matches.add(name);
            }
        }
        Collections.sort(matches);
        return Collections.unmodifiableList(matches);
    }

    /**
     * Determines the start index of the token being completed in the input string.
     */
    public static int getCompletionRangeStart(String input) {
        if (input == null || !input.contains(" ")) return 0;
        return input.lastIndexOf(' ') + 1;
    }

    /**
     * Merges server completions and client completions into a deduplicated array.
     */
    public static String[] mergeCompletions(String[] serverCompletions, List<String> clientCompletions) {
        Set<String> merged = new LinkedHashSet<>();
        if (serverCompletions != null) {
            for (String s : serverCompletions) {
                if (s != null && !s.isEmpty()) merged.add(s);
            }
        }
        if (clientCompletions != null) {
            for (String c : clientCompletions) {
                if (c != null && !c.isEmpty()) merged.add(c);
            }
        }
        return merged.toArray(new String[0]);
    }

    /**
     * Collects all known player names available locally without sending any extra network packets:
     * - Tab list players
     * - Party members
     * - Recent chat senders
     */
    public static Set<String> collectKnownNames() {
        Set<String> names = new LinkedHashSet<>();
        // Tab list
        names.addAll(LadsGameBridge.get().getOnlinePlayerNames());
        // Party members
        names.addAll(HypixelMods.partyMembers());
        // Recent chat senders
        names.addAll(HypixelChat.getRecentSenders());
        return names;
    }
}
