package com.thelads.core.client.hypixel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel's server chat as plain text, shared by every Hypixel feature (Hypixel mods, and kill banners when they need it). Each
 * pattern matches a whole line of a server message (split at newlines), so a player's chat, which always starts with their name,
 * a rank or a channel ("[MVP+] Name: ...", "Party > ..."), never matches. Centered summary lines (game end) start with spaces,
 * which player chat cannot. Formats as Hypixel sends them in 2026 (written from its own messages, not copied from another mod).
 */
public final class HypixelChat {
    private static final Pattern FORMAT = Pattern.compile("§.");
    private static final Pattern PLAYER_CHAT = Pattern.compile("^(?:"
        + "(?:(?:\\[[^\\]]+\\] )?\\w{1,16}(?: \\[\\w+\\])?: )"
        + "|(?:(?:Party|Guild|Officer|Shout) > )"
        + "|(?:(?:From|To) (?:\\[[^\\]]+\\] )?\\w{1,16}: )"
        + ").*");

    // Party list lines and chat notifications
    private static final Pattern PARTY_ROLE_LINE = Pattern.compile("Party (?:Leader|Moderators?|Members?):\\s*(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_SEP = Pattern.compile("^[§r\\s-]*(?:-{20,}|={20,})[§r\\s-]*$");
    private static final Pattern PARTY_COUNT = Pattern.compile("Party Members \\((\\d+)\\)");
    private static final Pattern PARTY_DISBAND = Pattern.compile("(?i)(?:\\[Party\\] )?(?:"
        + "The party was disbanded.*"
        + "|You (?:are not currently|are not) in a party.*"
        + "|You (?:have left|left) the party.*"
        + "|You (?:have been|were) kicked from the party.*"
        + "|You were removed from the party.*"
        + ")");
    private static final Pattern PARTY_JOIN = Pattern.compile("(?i)(?:\\[Party\\] )?(?:\\[[^\\]]+\\] )?(\\w{1,16}) joined the party[.!]*");
    private static final Pattern PARTY_YOU_JOIN = Pattern.compile("(?i)You (?:have )?joined (?:\\[[^\\]]+\\] )?(\\w{1,16})'s party[.!]*");
    private static final Pattern PARTY_LEAVE = Pattern.compile("(?i)(?:\\[Party\\] )?(?:\\[[^\\]]+\\] )?(\\w{1,16}) (?:has left|left) the party[.!]*");
    private static final Pattern PARTY_KICK = Pattern.compile("(?i)(?:\\[Party\\] )?(?:\\[[^\\]]+\\] )?(\\w{1,16}) (?:(?:has been|was) removed from the party|was kicked from the party).*");

    // Elimination and death patterns
    private static final Pattern FINAL_KILL_LINE = Pattern.compile("(?i)^(?:\\[[^\\]]+\\]\\s*)*(\\w{1,16})\\b.*\\bFINAL KILL!.*");
    private static final Pattern ELIMINATED_LINE = Pattern.compile("(?i)^(?:\\[[^\\]]+\\]\\s*)*(\\w{1,16})\\b.*\\b(?:has been|was) eliminated!?.*");
    private static final Pattern DISCONNECT_LINE = Pattern.compile("(?i)^(?:\\[[^\\]]+\\]\\s*)*(\\w{1,16})\\b.*\\b(?:disconnected|has quit|left the game)[.!]*");
    private static final Pattern GENERAL_KILL_LINE = Pattern.compile("(?i)^(?:\\[[^\\]]+\\]\\s*)*(\\w{1,16})\\b\\s+(?:was (?:killed|slain|shot|knocked into the void|spooked|blown up|thrown into the void|pummeled|fireballed)|fell into the void|died|hit the ground|burned to death|went up in flames|drowned|suffocated|withered away)\\b.*");
    private static final Pattern SELF_OUT_LINE = Pattern.compile("(?i).*(?:You have been eliminated!|You are now a spectator!?|Click here to play again!).*");
    /** The end-of-game summary every mode prints, centered (leading spaces): its ranking or winner line. */
    private static final Pattern GAME_END = Pattern.compile("\\s+(?:"
        + "1st\\b(?: Killer| Place)?\\s*[-:].*"        // 1st Killer - [MVP+] Name - 7 / 1st Place - Name / 1st - Name
        + "|#1 .+"                                     // #1 Name (12:34:56)
        + "|Winn(?:er|ers|ing Team)\\b.*"               // Winner: Name / Winners: A, B / Winner - RED / Winning Team - Red
        + "|.*\\bWINNER!.*"                            // Duels: WINNER! [VIP] A  B
        + "|.+ won the game!"                          // Name won the game! / Cops won the game!
        + "|GAME OVER!|Game over!"
        + "|Murderer: .+"                              // Murder Mystery
        + "|Alpha Infected: .+"
        + "|You survived \\d+ rounds!|Zombies - .+"    // Zombies
        + "|(?:Survivors \\(\\d+\\):|Nobody survived!|Survivor:).*"
        + "|[A-Za-z]+(?: [A-Za-z0-9]+){0,3} - \\d{1,2}:\\d\\d" // Duels: Classic Duel - 2:13 / Bridge CTF Duel - 12:34
        + "|They captured all wools!"                  // Capture the Wool
        + "|(?:\\[[^\\]]+\\] )?\\w{1,16} - \\d+ Final Kills" // Mega Walls
        + "|[\\d.]+k?/[\\d.]+k? \\w+|. YOUR STATISTICS ." // Warlords
        + "|Player of the match: .+"
        + ")\\s*");
    /** Sent once a recorded game is over (most minigames). */
    private static final String REPLAY = "This game has been recorded. Click here to watch the Replay!";
    /** The countdown's last second, just before the game starts. */
    private static final Pattern GAME_START = Pattern.compile("(?:The game starts in|The game is starting in|Cages open in:) 1 second!?");
    private static final Pattern FRIEND_REQUEST = Pattern.compile("Friend request from (?:\\[[^\\]]+\\] )?(\\w{1,16})\\s*");
    /** Hypixel's (Bukkit's) answer to a command it does not have, and vanilla's (QA, singleplayer). */
    private static final Pattern UNKNOWN_COMMAND = Pattern.compile("Unknown (?:or incomplete )?command\\b.*");
    /** /locraw's answer: {"server":"mini123A","gametype":"BEDWARS","mode":"BEDWARS_EIGHT_ONE","map":"Lighthouse"}. */
    private static final Pattern LOCRAW = Pattern.compile("\\{\"server\":\"[^\"]*\".*\\}");
    private static final Pattern LOCRAW_FIELD = Pattern.compile("\"(gametype|mode)\":\"([A-Za-z0-9_]+)\"");

    /** Chat Cleanup's kinds of line, in its option order. */
    public enum Spam {
        LOBBY_JOIN("Lobby joins", Pattern.compile("(?:>>> )?\\[[^\\]]+\\] \\w{1,16} [A-Za-z ]{3,30} the lobby!(?: <<<)?")),
        MYSTERY_BOX("Boxes and Soul Well", Pattern.compile("(?:✦ )?(?:\\[[^\\]]+\\] )?\\w{1,16} (?:found|has found) .*(?:Mystery Box|in the Soul Well)!")),
        GIFTS("Rank gifts", Pattern.compile("They have gifted \\d+ ranks? so far!|(?:\\[[^\\]]+\\] )?\\w{1,16} gifted .*\\branks?\\b.*")),
        TIPS("Tips", Pattern.compile("You (?:\\(anonymously\\) )?tipped .+|You were tipped by .+|You(?:'ve)? already tipped .+"
            + "|No one has a network booster active right now!.*|That player is not online, try another user!|Slow down! You can only use /tip .+")),
        BOOSTERS("Boosters", Pattern.compile("➲ .+|✘ \\w{1,16}'s Network Booster expired!|.*Network Boosters? (?:are available )?at https?://store\\.hypixel\\.net.*")),
        ADS("Store ads", Pattern.compile(".*\\bstore\\.hypixel\\.net\\b.*|You are playing on .*MC\\.HYPIXEL\\.NET.*")),
        WATCHDOG("Watchdog news", Pattern.compile("\\[WATCHDOG ANNOUNCEMENT\\]|Watchdog has banned [\\d,]+ players? in the last .+"
            + "|Staff have banned an additional [\\d,]+ in the last .+|Blacklisted modifications are a bannable offense!")),
        KARMA("Karma", Pattern.compile("\\+\\d+ Karma!")),
        /** Off by default: game rewards some players want to see. */
        REWARDS("Coins and XP lines", false, Pattern.compile("\\+[\\d,]+ (?:coins|tokens|Souls?|.*Experience|.*EXP)\\b.*")),
        /** Off by default: other players' public chat (party, guild and private messages never) advertising parties, guilds, links. */
        PLAYER_ADS("Player ads", false, Pattern.compile("(?i)(?!(?:party|guild|officer|friend) > |from |to )[^:]{1,60}: (?:"
            + ".*\\b(?:party join|p join|join (?:my |our )?(?:party|guild)|guild join|g join|duel me|lowball\\w*)\\b.*"
            + "|.*(?:/visit|/p join|/party join|/g join|discord\\.gg|youtube\\.com|youtu\\.be|twitch\\.tv).*"
            + "|.*\\b(?:buy|gift|give) me\\b.*\\b(?:rank|vip|mvp)\\b.*)"));

        public final String option;
        public final boolean on;
        private final Pattern line;
        Spam(String option, Pattern line) { this(option, true, line); }
        Spam(String option, boolean on, Pattern line) { this.option = option; this.on = on; this.line = line; }
    }

    private HypixelChat() {}

    /** The text without § formatting codes. */
    public static String plain(String text) { return text == null ? "" : FORMAT.matcher(text).replaceAll(""); }

    private static String[] lines(String message) { return plain(message).split("\n"); }

    private static boolean anyLine(String message, Pattern pattern) {
        for (String line : lines(message)) if (pattern.matcher(line).matches()) return true;
        return false;
    }

    /** A line of the end-of-game summary, or the replay notice: the game is over. */
    public static boolean gameEnd(String message) {
        for (String line : lines(message)) if (GAME_END.matcher(line).matches() || line.trim().equals(REPLAY)) return true;
        return false;
    }

    /** The countdown's last second: the game starts now. */
    public static boolean gameStart(String message) { return anyLine(message, GAME_START); }

    /** Who sent a friend request ("Friend request from [VIP] Name"), or null. */
    public static String friendRequest(String message) {
        for (String line : lines(message)) {
            Matcher m = FRIEND_REQUEST.matcher(line.trim());
            if (m.matches()) return m.group(1);
        }
        return null;
    }

    /** The server does not know the command just sent. */
    public static boolean unknownCommand(String message) { return anyLine(message, UNKNOWN_COMMAND); }

    /** /locraw's JSON answer as {gametype, mode} (mode null in a lobby), or null when the line is not one. */
    public static String[] location(String message) {
        String line = plain(message).trim();
        if (!LOCRAW.matcher(line).matches()) return null;
        String[] found = new String[2];
        Matcher m = LOCRAW_FIELD.matcher(line);
        while (m.find()) found["gametype".equals(m.group(1)) ? 0 : 1] = m.group(2);
        return found;
    }

    /** The /play name of a /locraw game mode, or null for places without games (lobbies, SkyBlock, Housing, Pit, Limbo). */
    public static String playCommand(String gametype, String mode) {
        if (gametype == null || mode == null || mode.equalsIgnoreCase("lobby")) return null;
        String m = mode.toLowerCase(Locale.ROOT);
        switch (gametype.toUpperCase(Locale.ROOT)) {
            case "SKYBLOCK": case "HOUSING": case "PIT": case "LIMBO": case "REPLAY": case "MAIN": case "PROTOTYPE": return null;
            case "SURVIVAL_GAMES": return "/play blitz_" + m;     // Blitz: solo_normal -> blitz_solo_normal (solo_normal is SkyWars)
            case "TNTGAMES": return "/play tnt_" + m;             // TNTRUN -> tnt_tntrun
            case "WALLS3": return "/play mw_" + m;                // Mega Walls: standard -> mw_standard
            case "UHC": return "/play uhc_" + m;
            case "ARCADE": return m.startsWith("arcade_") ? "/play " + m : "/play arcade_" + m;
            default: return "/play " + m;                         // BEDWARS_EIGHT_ONE, solo_insane, DUELS_CLASSIC_DUEL, MURDER_CLASSIC, ...
        }
    }

    /** The Chat Cleanup kind of a server message, or null when it is none of them. */
    public static Spam spam(String message) {
        for (String line : lines(message)) {
            String text = line.trim();
            if (text.isEmpty()) continue;
            for (Spam kind : Spam.values()) if (kind.line.matcher(text).matches()) return kind;
        }
        return null;
    }

    /** True when the line is player or channel chat, which server notices never are. */
    public static boolean isPlayerChat(String line) {
        String p = plain(line).trim();
        return PLAYER_CHAT.matcher(p).matches();
    }

    /** Does this gametype have supported elimination/kill tracking? */
    public static boolean isModeKnown(String gametype) {
        if (gametype == null) return false;
        String g = gametype.toUpperCase(Locale.ROOT);
        return g.equals("BEDWARS") || g.equals("SKYWARS") || g.equals("DUELS");
    }

    /** Parses player names listed in a /party list role line (Party Leader:, Party Members:, etc.). */
    public static List<String> partyMembers(String message) {
        List<String> names = new ArrayList<>();
        for (String line : lines(message)) {
            if (isPlayerChat(line)) continue;
            String p = line.trim();
            Matcher m = PARTY_ROLE_LINE.matcher(p);
            if (!m.matches()) continue;
            String rest = m.group(1);
            rest = rest.replaceAll("\\[[^\\]]+\\]", " ");
            rest = rest.replaceAll("[●?•,»|:]", " ");
            Matcher nameMatcher = Pattern.compile("\\b[A-Za-z0-9_]{1,16}\\b").matcher(rest);
            while (nameMatcher.find()) {
                String name = nameMatcher.group();
                if (!name.equalsIgnoreCase("Party") && !name.equalsIgnoreCase("Leader")
                    && !name.equalsIgnoreCase("Moderator") && !name.equalsIgnoreCase("Moderators")
                    && !name.equalsIgnoreCase("Member") && !name.equalsIgnoreCase("Members")) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /** Who joined the party, or "YOU_JOINED:<leader>" if you joined someone's party, or null. */
    public static String partyJoin(String message) {
        for (String line : lines(message)) {
            if (isPlayerChat(line)) continue;
            String p = line.trim();
            Matcher y = PARTY_YOU_JOIN.matcher(p);
            if (y.matches()) return "YOU_JOINED:" + y.group(1);
            Matcher j = PARTY_JOIN.matcher(p);
            if (j.matches()) return j.group(1);
        }
        return null;
    }

    /** Who left or was kicked/removed from the party, or null. */
    public static String partyLeave(String message) {
        for (String line : lines(message)) {
            if (isPlayerChat(line)) continue;
            String p = line.trim();
            Matcher l = PARTY_LEAVE.matcher(p);
            if (l.matches()) return l.group(1);
            Matcher k = PARTY_KICK.matcher(p);
            if (k.matches()) return k.group(1);
        }
        return null;
    }

    /** True when the party was disbanded, or you left / were kicked / not in a party. */
    public static boolean partyDisbanded(String message) {
        for (String line : lines(message)) {
            if (isPlayerChat(line)) continue;
            if (PARTY_DISBAND.matcher(line.trim()).matches()) return true;
        }
        return false;
    }

    /** True for lines that are part of /party list output. */
    public static boolean isPartyListLine(String message) {
        for (String line : lines(message)) {
            if (isPlayerChat(line)) return false;
            String p = line.trim();
            if (p.isEmpty()) continue;
            if (PARTY_SEP.matcher(p).matches()) return true;
            if (PARTY_COUNT.matcher(p).matches()) return true;
            if (PARTY_ROLE_LINE.matcher(p).matches()) return true;
            if (PARTY_DISBAND.matcher(p).matches()) return true;
        }
        return false;
    }

    /** Who was eliminated or killed in this line for {@code gametype}, or null. */
    public static String eliminatedPlayer(String gametype, String message) {
        for (String line : lines(message)) {
            if (isPlayerChat(line)) continue;
            String p = line.trim();
            Matcher fk = FINAL_KILL_LINE.matcher(p);
            if (fk.matches()) return fk.group(1);
            Matcher el = ELIMINATED_LINE.matcher(p);
            if (el.matches()) return el.group(1);
            Matcher dc = DISCONNECT_LINE.matcher(p);
            if (dc.matches()) return dc.group(1);
            if (gametype != null && (gametype.equalsIgnoreCase("SKYWARS") || gametype.equalsIgnoreCase("DUELS"))) {
                Matcher gk = GENERAL_KILL_LINE.matcher(p);
                if (gk.matches()) return gk.group(1);
            }
        }
        return null;
    }

    /** True if this is a player speaking in party chat (e.g. Party > [MVP+] Player: text). */
    public static boolean isPartyChat(String message) {
        for (String line : lines(message)) {
            String p = line.trim();
            if (p.startsWith("Party >")) return true;
        }
        return false;
    }

    /** True when this line indicates the local player died or was eliminated (not a temporary respawn death). */
    public static boolean isSelfOut(String gametype, String message, String myName) {
        for (String line : lines(message)) {
            if (isPlayerChat(line)) continue;
            String p = line.trim();
            if (p.toLowerCase(Locale.ROOT).contains("respawn")) continue;
            if (SELF_OUT_LINE.matcher(p).matches()) return true;
            if (myName != null && !myName.isEmpty()) {
                String victim = eliminatedPlayer(gametype, line);
                if (victim != null && victim.equalsIgnoreCase(myName)) return true;
            }
        }
        return false;
    }
}
