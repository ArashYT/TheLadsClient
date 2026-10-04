package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The Chat Heads module: the sender's face (with its hat layer) before a chat message. Signed player chat names its sender; for
 * every other message (server and plugin chat, joins, 1.8.9 chat) the adapters ask {@link #sender} which tab-list player it is
 * from. The head and a 2 px gap take {@link #WIDTH} chat pixels, so the adapters wrap those messages that much narrower.
 */
public final class ChatHeads {
    public static final String NAME = "Chat Heads";
    public static final String BY_NAME = "Detect by name", ALIGNED = "Keep text aligned";
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

    /** Chat pixels a message's text moves right: room for its head, or for every message with Keep text aligned. */
    public static int offset(boolean head) {
        return enabled() && (head || option(ALIGNED, false)) ? WIDTH : 0;
    }

    /** True once after the module or Keep text aligned changed: the adapter then wraps the chat's lines again. */
    public static boolean layoutChanged() {
        int now = enabled() ? option(ALIGNED, false) ? 2 : 1 : 0;
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
            String display = plain(displayName).trim();
            if (!display.isEmpty()) names.putIfAbsent(display, player);
        }
        String nickname = nicknames.get(account);
        if (nickname != null) names.putIfAbsent(nickname.toLowerCase(Locale.ROOT), player);
    }

    /**
     * The player whose name comes first in the text, so a rank or prefix before the sender is skipped and names later in the
     * message are not the sender. Formatting codes are ignored, case too; a name counts only as a whole word ("Steve" is not in
     * "Steve2"), and where two names start at the same place the longer one wins. Null when no name is in the text.
     */
    public static <T> T sender(String text, Map<String, T> names) {
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
        return best;
    }

    /** Lower-case text without formatting codes. */
    private static String plain(String text) {
        return FORMATTING.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static int find(String text, String name) {
        if (name.isEmpty()) return -1;
        boolean wordStart = word(name.charAt(0)), wordEnd = word(name.charAt(name.length() - 1));
        for (int at = text.indexOf(name); at >= 0; at = text.indexOf(name, at + 1)) {
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
