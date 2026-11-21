package com.thelads.core.v26_2.gui;

import com.thelads.core.client.title.ButtonIcons;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The icon of a button or slider from its message (ButtonIcons): vanilla's keys, an option's caption inside its "%s: %s" wrapper,
 * the Lads' own plain labels. No allocation, safe every frame; null for none. A plain-text label with siblings ("Save" + " (3)") has no icon.
 */
public final class MessageIcons {
    private MessageIcons() {}

    public static String of(Component message) {
        if (message == null || packIcon(message)) return null;
        var contents = message.getContents();
        if (contents instanceof TranslatableContents t) {
            String key = t.getKey();
            if (ButtonIcons.wraps(key)) {
                Object[] args = t.getArgs();
                if (args.length > 0 && args[0] instanceof Component caption && caption.getContents() instanceof TranslatableContents c) key = c.getKey();
            }
            return ButtonIcons.of(key, null);
        }
        return message.getSiblings().isEmpty() && contents instanceof PlainTextContents l ? ButtonIcons.of(null, l.text()) : null;
    }

    /** Whether the message's text (an option's caption too) already carries a resource pack's icon glyph: then no Lads icon. */
    public static boolean packIcon(Component message) {
        if (message == null) return false;
        var contents = message.getContents();
        if (contents instanceof TranslatableContents t) {
            Language lang = Language.getInstance();
            if (ButtonIcons.hasPackIcon(lang.getOrDefault(t.getKey()))) return true;
            Object[] args = t.getArgs();
            return args.length > 0 && args[0] instanceof Component caption && caption.getContents() instanceof TranslatableContents c
                && ButtonIcons.hasPackIcon(lang.getOrDefault(c.getKey()));
        }
        return contents instanceof PlainTextContents l && ButtonIcons.hasPackIcon(l.text());
    }
}
