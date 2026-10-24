package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.TextOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The Chat module's chat tweaks (parity-1023), shared by every version: Compact Chat, Chat Tabs (All plus the Custom Tabs), Chat
 * Search, Shift+Enter Keeps Chat Open, the Chat Peek and Toggle Chat keys, Mention Highlight and Mention Sound, Profanity Filter,
 * Shortcuts and Translate Chat (ChatTranslator). Each adapter calls {@link #tick} once a client tick and its hooks read the values
 * cached there. Message work (tabs, search, mentions, profanity, compact) runs once per message as it arrives or is laid out, never
 * per frame. All display only: what the server sees is unchanged, except Shortcuts, which expand what the player typed.
 */
public final class ChatTweaks {
    public static final String COMPACT = "Compact Chat", TABS = "Chat Tabs", CUSTOM_TABS = "Custom Tabs", SEARCH = "Chat Search",
        KEEP_OPEN = "Shift+Enter Keeps Chat Open", PEEK_KEY = "Chat Peek key", TOGGLE_KEY = "Toggle Chat key",
        SERVER_HISTORY = "History per Server", MENTIONS = "Mention Highlight", MENTION_SOUND = "Mention Sound",
        PROFANITY = "Profanity Filter", SHORTCUTS = "Shortcuts", BUTTONS = "Copy and Delete Buttons", TRANSLATE = "Translate Chat",
        TRANSLATE_TO = "Translate To", FUZZY = "Fuzzy Command Suggestions";
    /**
     * Unlimited Command Length (its own module, gated, off by default): commands up to 32767 characters, only in singleplayer, where
     * the game's own server takes them (a multiplayer server rejects or kicks for a command past vanilla's limit).
     */
    public static final String LONG_COMMANDS = "Unlimited Command Length";
    public static final int LONG_COMMAND_LIMIT = 32767;
    /** The built-in tabs, shown only on Hypixel (as Chatting's): its party, guild and private message lines. Custom Tabs: "Name=regex;Name=regex". */
    static final String HYPIXEL_TABS = "Party=^(Party|P) >;Guild=^(Guild|G) >;PM=^(From|To) ";
    /** Chat tab row and search box height, just above the input bar (clear of the chat lines on every version). */
    public static final int BAR_HEIGHT = 12, BAR_BOTTOM = 15, SEARCH_WIDTH = 100;
    static final long COMPACT_WINDOW = 60_000, PING_GAP = 1_000;
    private static final Pattern PROFANE = Pattern.compile("(?i)\\b(?:fuck|shit|bitch|asshole|bastard|cunt|dick|fag|nigg|slut|whore|retard)\\w*");

    public static boolean compact, tabs, search, keepOpen, serverHistory, mentions, mentionSound, profanity, buttons, translate, fuzzy;
    /** Translate To's Google Translate code, or null for the game's language. */
    public static String translateTo;
    /** Chat Peek is held / Toggle Chat hid the chat: the adapters poll their keys. */
    public static volatile boolean peeking, hidden;
    /** Changes whenever the tab or search filter does: the adapters lay the chat out again when they see a new value. */
    public static int filterRevision;

    private static String[] tabNames = {"All"};
    private static Pattern[] tabPatterns = {null};
    private static String tabsSource, shortcutsSource, selfName;
    private static boolean tabsHypixel;
    private static Map<String, String> shortcuts = Map.of();
    private static Pattern self;
    private static int activeTab;
    private static String query = "";
    private static String compactLast;
    private static int compactCount;
    private static long compactAt, pingAt = Long.MIN_VALUE / 2;

    private ChatTweaks() {}

    /** Once a client tick, on the client thread. */
    public static void tick() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        boolean on = chat != null && chat.isEnabled();
        compact = on && bool(chat, COMPACT);
        boolean tabsNow = on && bool(chat, TABS), searchNow = on && bool(chat, SEARCH);
        keepOpen = on && bool(chat, KEEP_OPEN);
        serverHistory = on && bool(chat, SERVER_HISTORY);
        mentions = on && bool(chat, MENTIONS);
        mentionSound = on && bool(chat, MENTION_SOUND);
        profanity = on && bool(chat, PROFANITY);
        buttons = on && bool(chat, BUTTONS);
        translate = on && bool(chat, TRANSLATE);
        fuzzy = on && bool(chat, FUZZY);
        translateTo = on && chat.getOption(TRANSLATE_TO) instanceof DropdownOption to ? ChatTranslator.code(to.getIndex()) : null;
        boolean hypixel = tabsNow && HypixelSafetyManager.isHypixel();
        if (on && chat.getOption(CUSTOM_TABS) instanceof TextOption text && (text.getValue() != tabsSource || hypixel != tabsHypixel))
            buildTabs(tabsHypixel = hypixel, tabsSource = text.getValue());
        if (on && chat.getOption(SHORTCUTS) instanceof TextOption text && text.getValue() != shortcutsSource)
            shortcuts = parseShortcuts(shortcutsSource = text.getValue());
        if (!on) {
            shortcuts = Map.of();
            shortcutsSource = null;
        }
        if (tabsNow != tabs || searchNow != search) {
            tabs = tabsNow;
            search = searchNow;
            if (!tabs) activeTab = 0;
            if (!search) query = "";
            filterRevision++;
        }
        if (!on) hidden = peeking = false;
    }

    private static boolean bool(Module module, String name) {
        return module.getOption(name) instanceof BoolOption option && option.get();
    }

    /** Copy and Delete Buttons: two boxes this wide right of the chat line under the pointer, while the chat is open. */
    public static final int BUTTON = 10;
    public static final String COPY_LABEL = "C", DELETE_LABEL = "×";

    /** The Copy (0) or Delete (1) button at {@code mouseX}, the buttons starting at the chat's right edge {@code right}; -1 for neither. */
    public static int button(int mouseX, int right) {
        if (mouseX < right || mouseX >= right + 2 * BUTTON + 1) return -1;
        return mouseX < right + BUTTON ? 0 : mouseX > right + BUTTON ? 1 : -1;
    }

    /** Unlimited Command Length applies: the module is on (and allowed) and the world is singleplayer. */
    public static boolean longCommands(boolean singleplayer) {
        if (!singleplayer) return false;
        Module module = ModuleManager.getInstance().getModule(LONG_COMMANDS);
        return module != null && module.isEnabled();
    }

    /** History per Server's key: the multiplayer address, or null in singleplayer and menus. */
    public static String serverKey(String address) {
        return address == null || address.isEmpty() || "Singleplayer".equalsIgnoreCase(address) || "Unknown".equalsIgnoreCase(address)
            ? null : address;
    }

    // Tabs and search

    /** All, then the built-in Hypixel tabs while on Hypixel, then the player's Custom Tabs (every server). */
    static void buildTabs(boolean hypixel, String custom) {
        List<String> names = new ArrayList<>();
        List<Pattern> patterns = new ArrayList<>();
        names.add("All");
        patterns.add(null);
        if (hypixel) parseTabs(HYPIXEL_TABS, names, patterns);
        parseTabs(custom, names, patterns);
        tabNames = names.toArray(new String[0]);
        tabPatterns = patterns.toArray(new Pattern[0]);
        activeTab = 0; // the tabs changed (another server, or edited): back to All
        filterRevision++;
    }

    private static void parseTabs(String source, List<String> names, List<Pattern> patterns) {
        for (String entry : source.split(";")) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) continue;
            try {
                patterns.add(Pattern.compile(entry.substring(eq + 1)));
                names.add(entry.substring(0, eq).trim());
            } catch (PatternSyntaxException invalid) {
                // a tab with a broken pattern is left out
            }
        }
    }

    public static int tabCount() { return tabs ? tabNames.length : 0; }
    public static String tabName(int index) { return tabNames[index]; }
    public static int activeTab() { return activeTab; }

    /** A tab was clicked: true when the chat must be laid out again. */
    public static boolean selectTab(int index) {
        if (!tabs || index < 0 || index >= tabNames.length || index == activeTab) return false;
        activeTab = index;
        filterRevision++;
        return true;
    }

    public static String query() { return query; }

    /** The search box changed: true when the chat must be laid out again. */
    public static boolean setQuery(String text) {
        String next = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (next.equals(query)) return false;
        query = next;
        filterRevision++;
        return true;
    }

    /** A tab other than All is chosen or a search is typed: only some messages are shown. */
    public static boolean filtering() {
        return activeTab > 0 || !query.isEmpty();
    }

    /** The message (its plain text) is shown in the chosen tab and matches the search. */
    public static boolean shows(String plain) {
        if (activeTab > 0 && !tabPatterns[activeTab].matcher(plain).find()) return false;
        return query.isEmpty() || plain.toLowerCase(Locale.ROOT).contains(query);
    }

    // Compact Chat

    /** A message arrived (its plain text): how often it has now come in a row within a minute; 1 for a new one. */
    public static int repeat(String plain, long now) {
        if (!compact || plain.isEmpty()) {
            compactLast = null;
            return 1;
        }
        if (plain.equals(compactLast) && now - compactAt <= COMPACT_WINDOW) {
            compactAt = now;
            return ++compactCount;
        }
        compactLast = plain;
        compactCount = 1;
        compactAt = now;
        return 1;
    }

    /** Something else was put in the chat: the next message never stacks onto the one before it. */
    public static void breakRepeat() {
        compactLast = null;
    }

    /** The suffix a stacked message gets: " (x2)". */
    public static String repeatSuffix(int count) {
        return " (x" + count + ")";
    }

    // Mentions and profanity

    /**
     * Censored and mentioned ranges ({start, end} pairs) in a message's plain text: {censor, mention}; null when the message keeps
     * its text. The player's own name counts as a mention unless it is where the sender's name goes (followed by ':', '>' or
     * '»', or after '<'), so the player's own lines never ping.
     */
    public static int[][] marks(String plain, String player) {
        int[] censor = profanity ? ranges(PROFANE.matcher(plain), null) : null;
        int[] mention = null;
        if (mentions || mentionSound) {
            Pattern name = selfPattern(player);
            if (name != null) mention = ranges(name.matcher(plain), plain);
        }
        if (censor == null && mention == null) return null;
        return new int[][] {censor == null ? new int[0] : censor, mention == null ? new int[0] : mention};
    }

    private static Pattern selfPattern(String player) {
        if (player == null || player.isEmpty()) return null;
        if (!player.equals(selfName)) {
            selfName = player;
            self = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(player) + "(?![A-Za-z0-9_])");
        }
        return self;
    }

    private static int[] ranges(Matcher matcher, String senderCheck) {
        int[] out = null;
        int count = 0;
        while (matcher.find()) {
            if (senderCheck != null && sender(senderCheck, matcher.start(), matcher.end())) continue;
            if (out == null) out = new int[4];
            else if (count + 2 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);
            out[count++] = matcher.start();
            out[count++] = matcher.end();
        }
        return out == null ? null : java.util.Arrays.copyOf(out, count);
    }

    static boolean sender(String text, int start, int end) {
        if (start > 0 && text.charAt(start - 1) == '<') return true;
        int i = end;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == ']')) i++;
        return i < text.length() && (text.charAt(i) == ':' || text.charAt(i) == '>' || text.charAt(i) == '»');
    }

    /** The message's text or style changes: something is censored, or a mention is highlighted. */
    public static boolean rewrites(int[][] marks) {
        return marks != null && (marks[0].length > 0 || mentions && marks[1].length > 0);
    }

    /** Mention Sound plays at most once a second. */
    public static boolean ping(int[][] marks, long now) {
        if (!mentionSound || marks == null || marks[1].length == 0 || now - pingAt < PING_GAP) return false;
        pingAt = now;
        return true;
    }

    /** One styled run of a rewritten message. */
    public interface Run<S> {
        void add(String text, S style, boolean mention);
    }

    /**
     * Rewrites a message given as its styled leaves (texts and styles, in order): censored ranges become asterisks and mentions are
     * marked, every leaf keeps its own style (colors, links, hover text).
     */
    public static <S> void rewrite(int[][] marks, List<String> texts, List<S> styles, Run<S> out) {
        int length = 0;
        for (String text : texts) length += text.length();
        byte[] flags = new byte[length];
        mark(flags, marks[0], (byte) 1);
        if (mentions) mark(flags, marks[1], (byte) 2);
        int pos = 0;
        for (int leaf = 0; leaf < texts.size(); leaf++) {
            String text = texts.get(leaf);
            int start = 0;
            while (start < text.length()) {
                byte flag = flags[pos + start];
                int end = start + 1;
                while (end < text.length() && flags[pos + end] == flag) end++;
                String part = text.substring(start, end);
                out.add(flag == 1 ? "*".repeat(part.length()) : part, styles.get(leaf), flag == 2);
                start = end;
            }
            pos += text.length();
        }
    }

    private static void mark(byte[] flags, int[] ranges, byte flag) {
        for (int i = 0; i + 1 < ranges.length; i += 2)
            for (int c = ranges[i]; c < ranges[i + 1] && c < flags.length; c++) if (flags[c] == 0) flags[c] = flag;
    }

    // Fuzzy Command Suggestions

    /**
     * A command word the typed letters fit loosely (modern versions, LiteralCommandNode): same first letter, the rest in order with
     * gaps ("gmd" for "gamemode", "tpa" for "tpaccept"). Words that start with the typed text are vanilla's own suggestions already.
     */
    public static boolean fuzzyMatch(String literal, String typed) {
        if (typed.length() < 2 || typed.length() > literal.length() || literal.charAt(0) != typed.charAt(0) || literal.startsWith(typed)) return false;
        int at = 1;
        for (int i = 1; i < typed.length(); i++) {
            at = literal.indexOf(typed.charAt(i), at);
            if (at < 0) return false;
            at++;
        }
        return true;
    }

    // Shortcuts

    static Map<String, String> parseShortcuts(String source) {
        Map<String, String> map = new HashMap<>();
        for (String entry : source.split(";")) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String from = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT), to = entry.substring(eq + 1).trim();
            if (!from.isEmpty() && !from.contains(" ") && !to.isEmpty()) map.put(from, to);
        }
        return map;
    }

    /** Shortcuts: the typed line with its first word replaced when it is a shortcut ("/l" to "/lobby"); unchanged otherwise. */
    public static String expand(String typed) {
        if (typed == null || shortcuts.isEmpty()) return typed;
        String trimmed = typed.trim();
        int space = trimmed.indexOf(' ');
        String first = (space < 0 ? trimmed : trimmed.substring(0, space)).toLowerCase(Locale.ROOT);
        String to = shortcuts.get(first);
        return to == null ? typed : space < 0 ? to : to + trimmed.substring(space);
    }
}
