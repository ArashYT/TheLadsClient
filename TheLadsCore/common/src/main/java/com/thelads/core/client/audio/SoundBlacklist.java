package com.thelads.core.client.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Comma-separated regexes matched (find) against "namespace:path" sound ids. Invalid entries are ignored. */
public final class SoundBlacklist {
    private static String lastSource;
    private static Pattern[] compiled = new Pattern[0];

    private SoundBlacklist() {}

    public static synchronized boolean matches(String source, String id) {
        if (source == null || source.trim().isEmpty() || id == null) return false;
        if (!source.equals(lastSource)) {
            List<Pattern> list = new ArrayList<Pattern>();
            for (String part : source.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) continue;
                try { list.add(Pattern.compile(p)); } catch (PatternSyntaxException ignored) {}
            }
            compiled = list.toArray(new Pattern[0]);
            lastSource = source;
        }
        for (Pattern p : compiled) if (p.matcher(id).find()) return true;
        return false;
    }

    public static synchronized void clear() { lastSource = null; compiled = new Pattern[0]; }
}
