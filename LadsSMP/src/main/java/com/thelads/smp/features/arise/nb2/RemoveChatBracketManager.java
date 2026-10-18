package com.thelads.smp.features.arise.nb2;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Removes standard angle brackets from chat messages, formatting output as 'Sender Message'
 * instead of '<Sender> Message' (Remove Chat Bracket).
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

        MutableComponent formatted = Component.empty()
                .append(sender)
                .append(" ")
                .append(content);

        return style != null ? formatted.withStyle(style) : formatted;
    }
}
