package com.thelads.core.client;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** User-configured reason matching; login/session failures and transfer handoffs never auto-retry. */
public final class ReconnectFilters {
    private ReconnectFilters() {}
    public static boolean blockedAuthentication(List<String> reasonKeys) {
        return reasonKeys.stream().anyMatch(key -> key.startsWith("disconnect.loginFailed") || key.contains("invalidSession")
            || key.contains("not_authenticated") || key.contains("authentication") || key.contains("invalid_public_key")
            || key.equals("disconnect.transfer") || key.contains("code_of_conduct"));
    }
    public static boolean allows(List<String> reasonKeys, String reason, List<String> keys, List<String> regexes, boolean onlyMatches) {
        if (blockedAuthentication(reasonKeys)) return false;
        boolean match = reasonKeys.stream().anyMatch(key -> keys.stream().anyMatch(condition -> !condition.isBlank() && key.contains(condition)));
        if (!match) for (String regex : regexes) {
            if (regex.isBlank()) continue;
            try { if (Pattern.compile(regex).matcher(reason).find()) { match = true; break; } }
            catch (PatternSyntaxException ignored) { /* A malformed imported expression does not crash the disconnect screen. */ }
        }
        return onlyMatches == match;
    }
    public static boolean contextMatches(String pattern, String id, boolean regex) {
        if (!regex) return pattern.equals(id);
        try { return Pattern.compile(pattern).matcher(id).matches(); }
        catch (PatternSyntaxException ignored) { return false; }
    }
}
