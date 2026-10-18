package com.thelads.smp.features.arise.nb2;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Removes standard angle brackets from chat messages, formatting output according
 * to NB2Config.chatFormat (defaults to '%s %s' instead of '<Sender> Message').
 */
public final class RemoveChatBracketManager {
    private RemoveChatBracketManager() {}

    public static boolean isRemoveChatBracketEnabled() {
        return NB2Config.get().removeChatBracket;
    }

    public static Component formatMessage(Component sender, Component content, Style style) {
        if (!isRemoveChatBracketEnabled()) {
            return null;
        }
        return formatMessage(sender, content, style, NB2Config.get().chatFormat);
    }

    public static Component formatMessage(Component sender, Component content, Style style, String formatPattern) {
        if (formatPattern == null || formatPattern.isEmpty()) {
            formatPattern = "%s %s";
        }

        MutableComponent formatted = Component.empty();

        if (formatPattern.contains("%1$s") && formatPattern.contains("%2$s")) {
            int idx1 = formatPattern.indexOf("%1$s");
            int idx2 = formatPattern.indexOf("%2$s");
            if (idx1 < idx2) {
                String prefix = formatPattern.substring(0, idx1);
                String middle = formatPattern.substring(idx1 + 4, idx2);
                String suffix = formatPattern.substring(idx2 + 4);
                if (!prefix.isEmpty()) formatted.append(Component.literal(prefix));
                formatted.append(sender);
                if (!middle.isEmpty()) formatted.append(Component.literal(middle));
                formatted.append(content);
                if (!suffix.isEmpty()) formatted.append(Component.literal(suffix));
                return style != null ? formatted.withStyle(style) : formatted;
            }
        }

        int firstIndex = formatPattern.indexOf("%s");
        if (firstIndex != -1) {
            int secondIndex = formatPattern.indexOf("%s", firstIndex + 2);
            if (secondIndex != -1) {
                String prefix = formatPattern.substring(0, firstIndex);
                String middle = formatPattern.substring(firstIndex + 2, secondIndex);
                String suffix = formatPattern.substring(secondIndex + 2);

                if (!prefix.isEmpty()) formatted.append(Component.literal(prefix));
                formatted.append(sender);
                if (!middle.isEmpty()) formatted.append(Component.literal(middle));
                formatted.append(content);
                if (!suffix.isEmpty()) formatted.append(Component.literal(suffix));

                return style != null ? formatted.withStyle(style) : formatted;
            }
        }

        // Fallback default
        formatted.append(sender).append(" ").append(content);
        return style != null ? formatted.withStyle(style) : formatted;
    }
}
