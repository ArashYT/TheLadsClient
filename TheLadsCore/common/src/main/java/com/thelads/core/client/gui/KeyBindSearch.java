package com.thelads.core.client.gui;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Controls search where the embedded Controlling does not run (1.8.9), with Controlling's (Searchables') terms: plain words
 * match a key binding's name, and name:, category: and key: match that part; quotes keep spaces in a value (key:"button 1").
 * Every term must match, ignoring case.
 */
public final class KeyBindSearch {
    private static final Pattern TERM = Pattern.compile("(?:[^\\s\"]|\"[^\"]*\"?)+");

    private KeyBindSearch() {}

    public static boolean matches(String query, String name, String category, String key) {
        Matcher term = TERM.matcher(query);
        while (term.find()) {
            String value = term.group().replace("\"", "").toLowerCase(Locale.ROOT), target = name;
            int colon = value.indexOf(':');
            String field = colon < 0 ? "" : value.substring(0, colon);
            if (field.equals("name") || field.equals("category") || field.equals("key")) {
                target = field.equals("category") ? category : field.equals("key") ? key : name;
                value = value.substring(colon + 1);
            }
            if (!target.toLowerCase(Locale.ROOT).contains(value)) return false;
        }
        return true;
    }
}
