package com.thelads.core.v26_2.feature;

import com.thelads.core.client.Nicknames;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Nametags renames (Your Display Name, Nicknames) for name tags, the tab list and arriving chat: display-only. */
public final class NativeNicknames {
    private static java.util.Map<String, String> lastNames = java.util.Collections.emptyMap();
    private static final java.util.Map<Component, Component> RENAME_CACHE = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private NativeNicknames() {}

    /** The text with renamed players, each piece keeping its style; the same instance when nothing is renamed. */
    public static Component rename(Component text) {
        if (text == null) return null;
        if (com.thelads.core.client.hypixel.LevelHead.isEnabled()) {
            String lvlTag = com.thelads.core.client.hypixel.LevelHead.formatLevelTag(text.getString());
            if (lvlTag != null) text = Component.literal(lvlTag).append(text);
        }
        var names = Nicknames.active(Minecraft.getInstance().getUser().getName());
        if (names.isEmpty()) return text;
        if (names != lastNames) {
            lastNames = names;
            RENAME_CACHE.clear();
        }
        Component cached = RENAME_CACHE.get(text);
        if (cached != null) return cached;
        List<String> parts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        text.visit((style, part) -> { parts.add(part); styles.add(style); return Optional.empty(); }, Style.EMPTY);
        String[] renamed = Nicknames.rename(parts.toArray(new String[0]), names);
        if (renamed == null) {
            RENAME_CACHE.put(text, text);
            return text;
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < renamed.length; i++)
            if (!renamed[i].isEmpty()) out.append(Component.literal(renamed[i]).setStyle(styles.get(i)));
        RENAME_CACHE.put(text, out);
        return out;
    }
}
