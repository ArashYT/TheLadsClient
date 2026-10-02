package com.thelads.core.client;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ToggleNametagsModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Nametags module renames: Your Display Name for the local player and Nicknames for others. Display-only: adapters rewrite
 * the text they draw (name tags, tab list) or a chat message once as it arrives; nothing is sent to the server.
 */
public final class Nicknames {
    private static String lastNicknames, lastDisplay, lastOwn;
    private static Map<String, String> active = Collections.emptyMap();
    private Nicknames() {}

    /** "Name=Nick, Name2=Nick2" as lower-case name -> nick; pairs without both sides are skipped. */
    public static Map<String, String> parse(String spec) {
        Map<String, String> names = new LinkedHashMap<>();
        if (spec == null) return names;
        for (String pair : spec.split(",")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String name = pair.substring(0, eq).trim(), nick = pair.substring(eq + 1).trim();
            if (!name.isEmpty() && !nick.isEmpty()) names.put(name.toLowerCase(Locale.ROOT), nick);
        }
        return names;
    }

    /** The renames to apply now (empty while the module is off); ownName is the local player's username. */
    public static Map<String, String> active(String ownName) {
        if (!(ModuleManager.getInstance().getModule("Nametags") instanceof ToggleNametagsModule tags) || !tags.isEnabled())
            return Collections.emptyMap();
        String nicks = tags.nicknames.getValue(), display = tags.displayName.getValue().trim();
        if (!nicks.equals(lastNicknames) || !display.equals(lastDisplay) || !String.valueOf(ownName).equals(lastOwn)) {
            Map<String, String> names = parse(nicks);
            if (!display.isEmpty() && ownName != null && !ownName.isEmpty()) names.put(ownName.toLowerCase(Locale.ROOT), display);
            active = names;
            lastNicknames = nicks; lastDisplay = display; lastOwn = String.valueOf(ownName);
        }
        return active;
    }

    /** One string; returns it unchanged when nothing matched. */
    public static String rename(String text, Map<String, String> names) {
        String[] renamed = rename(new String[] {text}, names);
        return renamed == null ? text : renamed[0];
    }

    /**
     * Replaces whole-word, case-insensitive names across styled segments, keeping one output string per segment so each keeps
     * its style. A name split over segments is written into the segment it starts in. A "§x" formatting code before a name
     * counts as a word boundary. Returns null when nothing matched.
     */
    public static String[] rename(String[] segments, Map<String, String> names) {
        if (names.isEmpty()) return null;
        String text = String.join("", segments);
        List<int[]> hits = new ArrayList<>(); // start, end
        List<String> nicks = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            if (i > 0 && word(text.charAt(i - 1)) && !(i > 1 && text.charAt(i - 2) == '§')) continue;
            for (Map.Entry<String, String> name : names.entrySet()) {
                int end = i + name.getKey().length();
                if (text.regionMatches(true, i, name.getKey(), 0, name.getKey().length()) && (end == text.length() || !word(text.charAt(end)))) {
                    hits.add(new int[] {i, end});
                    nicks.add(name.getValue());
                    i = end - 1;
                    break;
                }
            }
        }
        if (hits.isEmpty()) return null;
        String[] out = new String[segments.length];
        int p = 0, hit = 0, segmentEnd = 0;
        for (int s = 0; s < segments.length; s++) {
            segmentEnd += segments[s].length();
            StringBuilder b = new StringBuilder();
            while (p < segmentEnd) {
                if (hit < hits.size() && hits.get(hit)[0] == p) { b.append(nicks.get(hit)); p = hits.get(hit++)[1]; }
                else b.append(text.charAt(p++));
            }
            out[s] = b.toString();
        }
        return out;
    }

    private static boolean word(char c) { return Character.isLetterOrDigit(c) || c == '_'; }
}
