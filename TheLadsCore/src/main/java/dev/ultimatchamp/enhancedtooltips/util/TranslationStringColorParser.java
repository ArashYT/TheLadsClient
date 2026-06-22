/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 */
package dev.ultimatchamp.enhancedtooltips.util;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

public class TranslationStringColorParser {
    private static final Map<Character, Integer> COLOR_MAP = new HashMap<Character, Integer>();

    public static Integer[] getColorsFromTranslation(Component text) {
        return TranslationStringColorParser.getColorsFromTranslation(text.getString());
    }

    public static Integer[] getColorsFromTranslation(String text) {
        char[] charArray = text.toCharArray();
        Integer[] colors = new Integer[]{null, null};
        if (charArray.length < 2) {
            return colors;
        }
        for (int i = 0; i < charArray.length - 1; ++i) {
            int color;
            if (charArray[i] != '\u00a7' || (color = COLOR_MAP.getOrDefault(Character.valueOf(charArray[i + 1]), -1).intValue()) == -1) continue;
            if (colors[0] == null) {
                colors[0] = color;
                continue;
            }
            if (color == colors[0]) continue;
            colors[1] = color;
        }
        return colors;
    }

    static {
        COLOR_MAP.put(Character.valueOf('0'), 0);
        COLOR_MAP.put(Character.valueOf('1'), 170);
        COLOR_MAP.put(Character.valueOf('2'), 43520);
        COLOR_MAP.put(Character.valueOf('3'), 43690);
        COLOR_MAP.put(Character.valueOf('4'), 0xAA0000);
        COLOR_MAP.put(Character.valueOf('5'), 0xAA00AA);
        COLOR_MAP.put(Character.valueOf('6'), 0xFFAA00);
        COLOR_MAP.put(Character.valueOf('7'), 0xAAAAAA);
        COLOR_MAP.put(Character.valueOf('8'), 0x555555);
        COLOR_MAP.put(Character.valueOf('9'), 0x5555FF);
        COLOR_MAP.put(Character.valueOf('a'), 0x55FF55);
        COLOR_MAP.put(Character.valueOf('b'), 0x55FFFF);
        COLOR_MAP.put(Character.valueOf('c'), 0xFF5555);
        COLOR_MAP.put(Character.valueOf('d'), 0xFF55FF);
        COLOR_MAP.put(Character.valueOf('e'), 0xFFFF55);
        COLOR_MAP.put(Character.valueOf('f'), 0xFFFFFF);
    }
}

