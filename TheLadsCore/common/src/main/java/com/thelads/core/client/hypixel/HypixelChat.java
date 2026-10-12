package com.thelads.core.client.hypixel;

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
}
