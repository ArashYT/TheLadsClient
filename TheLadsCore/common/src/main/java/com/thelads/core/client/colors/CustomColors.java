package com.thelads.core.client.colors;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Clean-room Custom Colors parser and palette manager (OptiFine / MCPatcher format).
 * Loads color.properties and maps custom color values for text, sheep/wool, dyes,
 * potions, water, and particle tints.
 */
public final class CustomColors {
    public static final String[] FOLDERS = {"optifine/colormap/", "mcpatcher/colormap/"};

    private static final Map<String, Integer> PALETTE = new HashMap<String, Integer>();

    private CustomColors() {}

    public static void loadProperties(String text) {
        if (text == null || text.trim().isEmpty()) return;
        Properties props = new Properties();
        try {
            props.load(new StringReader(text));
        } catch (IOException e) {
            return;
        }

        for (String key : props.stringPropertyNames()) {
            String val = props.getProperty(key);
            if (val == null) continue;
            try {
                val = val.trim();
                if (val.startsWith("#")) val = val.substring(1);
                int color = Integer.parseInt(val, 16);
                PALETTE.put(key.toLowerCase(), color);
            } catch (NumberFormatException ignored) {}
        }
    }

    public static int getColor(String key, int defaultColor) {
        if (key == null) return defaultColor;
        Integer c = PALETTE.get(key.toLowerCase());
        return c != null ? c : defaultColor;
    }

    public static boolean hasColor(String key) {
        return key != null && PALETTE.containsKey(key.toLowerCase());
    }

    public static void clear() {
        PALETTE.clear();
    }
}
