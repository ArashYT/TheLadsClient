package com.thelads.core.client;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat mentions and profanity filter.
 */
public final class ChatFilter {
    private static final String[] PROFANITIES = {
        "fuck", "shit", "bitch", "asshole", "bastard", "cunt", "dick", "fag", "nigger", "slut", "whore"
    };

    private static Pattern profanityPattern = null;

    static {
        StringBuilder sb = new StringBuilder("(?i)\\b(");
        for (int i = 0; i < PROFANITIES.length; i++) {
            if (i > 0) sb.append("|");
            sb.append(Pattern.quote(PROFANITIES[i]));
        }
        sb.append(")\\b");
        profanityPattern = Pattern.compile(sb.toString());
    }

    private ChatFilter() {}

    public static boolean mentionHighlightEnabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Mention Highlight") instanceof BoolOption opt && opt.get();
    }

    public static boolean mentionPingEnabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Mention Ping") instanceof BoolOption opt && opt.get();
    }

    public static boolean profanityFilterEnabled() {
        Module chat = ModuleManager.getInstance().getModule("Chat");
        return chat != null && chat.isEnabled() && chat.getOption("Profanity Filter") instanceof BoolOption opt && opt.get();
    }

    public static boolean isMentioned(String text, String playerName) {
        if (!mentionHighlightEnabled() && !mentionPingEnabled()) return false;
        if (text == null || playerName == null || playerName.trim().isEmpty()) return false;
        Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(playerName) + "\\b");
        return p.matcher(text).find();
    }

    public static String filterProfanity(String text) {
        if (!profanityFilterEnabled() || text == null) return text;
        Matcher m = profanityPattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int len = m.group().length();
            m.appendReplacement(sb, "*".repeat(len));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
