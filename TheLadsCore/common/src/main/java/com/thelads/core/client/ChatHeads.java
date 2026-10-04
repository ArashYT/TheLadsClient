package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The Chat Heads module: the sender's face (with its hat layer) before a chat message. Signed player chat names its sender; for
 * every other message (server and plugin chat, joins, 1.8.9 chat) the adapters ask {@link #sender} which tab-list player it is
 * from. The head and a 2 px gap take {@link #WIDTH} chat pixels, so the adapters wrap those messages that much narrower.
 * Position "Before name" (the default) draws the head inline before the sender's name on a message's first line, and the text
 * after it moves right by {@link #WIDTH}; "Start of line" draws it before the first line and moves every line of the message.
 */
public final class ChatHeads {
    public static final String NAME = "Chat Heads";
    public static final String BY_NAME = "Detect by name", ALIGNED = "Keep text aligned", POSITION = "Position";
    /** Position choices; index 0, Before name, is the default. */
    public static final String[] POSITIONS = {"Before name", "Start of line"};
    public static final int WIDTH = 10;
    private static final Pattern FORMATTING = Pattern.compile("(?s)§.");
    private static int layout = -1;
    private ChatHeads() {}

    public static boolean enabled() {
        Module module = ModuleManager.getInstance().getModule(NAME);
        return module != null && module.isEnabled();
    }

    /** Messages without a signed sender get a head from the names in their text. */
    public static boolean byName() { return option(BY_NAME, true); }

    /** Position "Before name": the head sits inline before the sender's name, on the message's first line only. */
    public static boolean beforeName() {
        Module module = ModuleManager.getInstance().getModule(NAME);
        return module == null || !(module.getOption(POSITION) instanceof DropdownOption position) || position.getIndex() == 0;
    }

    /**
     * Chat pixels the head takes, so its message wraps that much narrower: room for its head, or (Start of line only) for every
     * message with Keep text aligned. Before name moves only the first line's text after the head; Start of line moves every line.
     */
    public static int offset(boolean head) {
        return enabled() && (head || !beforeName() && option(ALIGNED, false)) ? WIDTH : 0;
    }

    /** True once after the module, Position or Keep text aligned changed: the adapter then wraps the chat's lines again. */
    public static boolean layoutChanged() {
        int now = enabled() ? (option(ALIGNED, false) ? 2 : 1) + (beforeName() ? 2 : 0) : 0;
        boolean changed = layout >= 0 && layout != now;
        layout = now;
        return changed;
    }

    /**
     * Adds a tab-list player's names to {@code names} (lower-case keys): the account name, the tab-list display name without
     * formatting, and the Lads nickname ({@link Nicknames#active}), which arriving chat already shows instead of the name.
     */
    public static <T> void names(Map<String, T> names, T player, String name, String displayName, Map<String, String> nicknames) {
        String account = name.toLowerCase(Locale.ROOT);
        names.putIfAbsent(account, player);
        if (displayName != null) {
            String display = plain(displayName).trim().toLowerCase(Locale.ROOT);
            if (!display.isEmpty()) names.putIfAbsent(display, player);
        }
        String nickname = nicknames.get(account);
        if (nickname != null) names.putIfAbsent(nickname.toLowerCase(Locale.ROOT), player);
    }

    /** A message's sender and where the head goes before their name: {@code at} code points into the text without formatting codes. */
    public record Match<T>(T player, int at) {}

    /**
     * The player whose name comes first in the text, so a rank or prefix before the sender is skipped and names later in the
     * message are not the sender. Formatting codes are ignored, case too; a name counts only as a whole word ("Steve" is not in
     * "Steve2"), and where two names start at the same place the longer one wins. Null when no name is in the text.
     */
    public static <T> T sender(String text, Map<String, T> names) {
        Match<T> match = match(text, names);
        return match == null ? null : match.player();
    }

    /** As {@link #sender}, with where the name starts. Null when no name is in the text. */
    public static <T> Match<T> match(String text, Map<String, T> names) {
        String plain = plain(text);
        T best = null;
        int bestAt = Integer.MAX_VALUE, bestLength = 0;
        for (Map.Entry<String, T> name : names.entrySet()) {
            int at = find(plain, name.getKey());
            if (at >= 0 && (at < bestAt || at == bestAt && name.getKey().length() > bestLength)) {
                best = name.getValue();
                bestAt = at;
                bestLength = name.getKey().length();
            }
        }
        return best == null ? null : new Match<>(best, plain.codePointCount(0, bestAt));
    }

    /**
     * Where in a drawn line with formatting codes (1.8.9's "§c[Admin] §fSteve") the head goes: the char index of its visible code
     * point {@code at}, formatting codes skipped. 0 when the line has no more than {@code at} visible code points (the name is not
     * on it): the head then goes at the start.
     */
    public static int split(String line, int at) {
        int visible = 0;
        for (int i = 0; i < line.length(); ) {
            if (line.charAt(i) == '§' && i + 1 < line.length()) { i += 2; continue; }
            if (visible++ == at) return i;
            i += Character.charCount(line.codePointAt(i));
        }
        return 0;
    }

    /** Visible code points in text with formatting codes. */
    public static int visibleLength(String text) {
        String plain = plain(text);
        return plain.codePointCount(0, plain.length());
    }

    /** Text without formatting codes. */
    private static String plain(String text) {
        return FORMATTING.matcher(text).replaceAll("");
    }

    /** Where the lower-case {@code name} starts in {@code text} as a whole word, case ignored; -1 when it is not there. */
    private static int find(String text, String name) {
        if (name.isEmpty()) return -1;
        boolean wordStart = word(name.charAt(0)), wordEnd = word(name.charAt(name.length() - 1));
        for (int at = 0; at + name.length() <= text.length(); at++) {
            if (!text.regionMatches(true, at, name, 0, name.length())) continue;
            int end = at + name.length();
            if ((!wordStart || at == 0 || !word(text.charAt(at - 1))) && (!wordEnd || end == text.length() || !word(text.charAt(end))))
                return at;
        }
        return -1;
    }

    private static boolean word(char c) { return Character.isLetterOrDigit(c) || c == '_'; }

    private static boolean option(String name, boolean fallback) {
        Module module = ModuleManager.getInstance().getModule(NAME);
        return module != null && module.getOption(name) instanceof BoolOption bool ? bool.get() : fallback;
    }
}
