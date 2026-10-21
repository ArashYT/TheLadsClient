package com.thelads.core.v26_2.feature;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.thelads.core.client.hypixel.HypixelTabFill;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class HypixelTabFill26 {
    private HypixelTabFill26() {}

    public static Suggestions createSuggestions(String text, int cursor) {
        if (text == null || cursor < 0 || cursor > text.length()) return Suggestions.empty().join();
        String input = text.substring(0, cursor);
        if (!input.startsWith("/")) return Suggestions.empty().join();

        Set<String> knownNames = HypixelTabFill.collectKnownNames();
        List<String> candidates = HypixelTabFill.complete(input, knownNames);
        if (candidates.isEmpty()) return Suggestions.empty().join();

        int rangeStart = HypixelTabFill.getCompletionRangeStart(input);
        StringRange range = StringRange.between(rangeStart, cursor);

        List<Suggestion> list = new ArrayList<>(candidates.size());
        for (String c : candidates) {
            list.add(new Suggestion(range, c));
        }
        return new Suggestions(range, list);
    }

    public static Suggestions mergeSuggestions(Suggestions serverSugg, String text, int cursor) {
        Suggestions clientSugg = createSuggestions(text, cursor);
        if (serverSugg == null || serverSugg.isEmpty()) {
            return clientSugg;
        }
        if (clientSugg == null || clientSugg.isEmpty()) {
            return serverSugg;
        }

        List<Suggestion> merged = new ArrayList<>(serverSugg.getList());
        Set<String> existing = new LinkedHashSet<>();
        for (Suggestion s : merged) existing.add(s.getText());

        for (Suggestion c : clientSugg.getList()) {
            if (existing.add(c.getText())) {
                merged.add(c);
            }
        }
        return new Suggestions(serverSugg.getRange(), merged);
    }
}
