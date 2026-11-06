package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.title.ButtonIcons;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The icon of a button or slider from its message (ButtonIcons): vanilla's keys, an option's caption inside its "%s: %s" wrapper,
 * the Lads' own plain labels. No allocation, safe every frame; null for none. A plain-text label with siblings ("Save" + " (3)") has no icon.
 */
public final class MessageIcons {
    private MessageIcons() {}

    public static String of(Component message) {
        if (message == null) return null;
        var contents = message.getContents();
        if (contents instanceof TranslatableContents t) {
            String key = t.getKey();
            if (ButtonIcons.wraps(key)) {
                Object[] args = t.getArgs();
                if (args.length > 0 && args[0] instanceof Component caption && caption.getContents() instanceof TranslatableContents c) key = c.getKey();
            }
            return ButtonIcons.of(key, null);
        }
        return message.getSiblings().isEmpty() && contents instanceof LiteralContents l ? ButtonIcons.of(null, l.text()) : null;
    }
}
