package com.thelads.core.client.killbanner;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which deaths the client sees are the local player's kills, as they happen (no server statistics round trip):
 * <ul>
 * <li>the death event (or zero health) of an entity the local player damaged last, within 5 seconds, as vanilla credits a kill;</li>
 * <li>on servers whose games never really kill anyone (Hypixel and other plugin minigames), a server kill message that names a
 * player the local player hit within 10 seconds and the local player ("Bob was killed by Me.", "You killed Bob", "KILL! on Bob").</li>
 * </ul>
 * Each victim counts once per kill, whichever signal arrives first.
 */
public final class KillDetector {
    public enum Kind { PLAYER, MOB, BOSS }
    public record Kill(Kind kind, String victim, boolean headshot) {}

    static final long CREDIT = 5_000_000_000L, CHAT = 10_000_000_000L, ONCE = 5_000_000_000L, FORGET = 30_000_000_000L;
    private static final Pattern FORMAT = Pattern.compile("§.");
    private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    // Words that tie a victim to their killer in kill messages, themed ones included ("Bob was spooked by Me").
    private static final Pattern LINK = Pattern.compile("\\b(by|whilst|while|fighting|escape|escaping|against)\\b");
    private static final Pattern VERB = Pattern.compile("\\b(killed|slain|slew|eliminated|finished|destroyed|defeated|murdered|knocked|shot)\\b");
    private static final Pattern PASSIVE = Pattern.compile("\\b(by|was|were|got)\\b");
    // The Pit: "KILL! on [120] Bob"
    private static final Pattern KILL_TAG = Pattern.compile("^\\W*kill!?\\s+(on\\s+)?(\\[[^\\]]*\\]\\s*)*$");

    private static final class Victim {
        final Set<String> names = new LinkedHashSet<>();
        Kind kind;
        long mine = Long.MIN_VALUE, other = Long.MIN_VALUE, killed = Long.MIN_VALUE;
        boolean head;
    }
    private final Map<Integer, Victim> byId = new HashMap<>();

    public void reset() { byId.clear(); }

    /** The local player hit {@code id} (an attack, or a damage event that names the local player as its cause). */
    public void hitByMe(int id, Collection<String> names, Kind kind, boolean head, long now) {
        forget(now);
        Victim victim = byId.computeIfAbsent(id, key -> new Victim());
        for (String name : names) if (name != null && !name.isBlank()) victim.names.add(name.toLowerCase(Locale.ROOT));
        victim.kind = kind;
        // A plain damage event confirms the attack; it does not know where the hit landed.
        if (head || since(now, victim.mine) > 1_000_000_000L) victim.head = head;
        victim.mine = now;
    }

    /** A damage event on {@code id} named someone else as its cause: they now get the credit. */
    public void hitByOther(int id, long now) {
        Victim victim = byId.get(id);
        if (victim != null) victim.other = now;
    }

    /** {@code id} died on the client (death event or zero health). */
    public Kill died(int id, long now) {
        Victim victim = byId.get(id);
        if (victim == null || since(now, victim.mine) > CREDIT || victim.other > victim.mine) return null;
        return claim(victim, now);
    }

    /** A server (system) chat line; player chat never counts. */
    public Kill chat(String message, Collection<String> myNames, long now) {
        if (message == null || message.isBlank()) return null;
        String text = FORMAT.matcher(message).replaceAll("").toLowerCase(Locale.ROOT);
        Set<String> me = new LinkedHashSet<>();
        for (String name : myNames) if (name != null && !name.isBlank()) me.add(name.toLowerCase(Locale.ROOT));
        for (Victim victim : byId.values()) {
            if (victim.kind != Kind.PLAYER || since(now, victim.mine) > CHAT) continue;
            for (String name : victim.names)
                if (!me.contains(name) && killMessage(text, name, me)) return claim(victim, now);
        }
        return null;
    }

    private Kill claim(Victim victim, long now) {
        if (since(now, victim.killed) < ONCE) return null;
        victim.killed = now;
        return new Kill(victim.kind, victim.names.isEmpty() ? "" : victim.names.iterator().next(), victim.head);
    }

    /** Nanoseconds since {@code then}; never for an event that has not happened. */
    private static long since(long now, long then) { return then == Long.MIN_VALUE ? Long.MAX_VALUE : now - then; }

    private void forget(long now) {
        for (Iterator<Victim> it = byId.values().iterator(); it.hasNext(); )
            if (since(now, it.next().mine) > FORGET) it.remove();
    }

    /** Lower-case {@code text}: does it report {@code victim} killed by one of {@code me}? */
    static boolean killMessage(String text, String victim, Set<String> me) {
        int v = word(text, victim, 0);
        if (v < 0) return false;
        String before = text.substring(0, v);
        // "Name: ..." and "<Name> ..." are chat lines, whatever they say.
        if (before.contains(":") || before.contains("<")) return false;
        if (KILL_TAG.matcher(before).find()) return true;
        Set<String> subjects = new LinkedHashSet<>(me);
        subjects.add("you");
        for (String name : subjects) {
            int m = word(text, name, v + victim.length());
            if (m > 0) {
                String between = text.substring(v + victim.length(), m);
                if (between.length() > 64 || between.contains(":") || between.contains("assist")) continue;
                // The killer named right after "by" / "fighting" is the local player, not someone else first.
                var link = LINK.matcher(between);
                int end = -1;
                while (link.find()) end = link.end();
                if (end >= 0 && !BRACKETS.matcher(between.substring(end)).replaceAll("").matches(".*[a-z0-9].*")) return true;
            }
        }
        for (String name : subjects) {
            int m = word(before, name, 0);
            if (m < 0) continue;
            String between = before.substring(m + name.length());
            // "You killed Bob", "Me eliminated Bob"; never "You were killed by Bob".
            if (between.length() <= 32 && VERB.matcher(between).find() && !PASSIVE.matcher(between).find()) return true;
        }
        return false;
    }

    /** Index of {@code name} as a whole Minecraft-name token in {@code text} at or after {@code from}, else -1. */
    static int word(String text, String name, int from) {
        if (name.isEmpty()) return -1;
        for (int i = text.indexOf(name, from); i >= 0; i = text.indexOf(name, i + 1)) {
            int end = i + name.length();
            if ((i == 0 || !nameChar(text.charAt(i - 1))) && (end == text.length() || !nameChar(text.charAt(end)))) return i;
        }
        return -1;
    }

    private static boolean nameChar(char c) {
        return c == '_' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
    }

    /** The account name plus the name shown (ranks, tags and team prefixes in brackets removed): servers write either. */
    public static Set<String> names(String profileName, String shownName) {
        Set<String> names = new LinkedHashSet<>();
        if (profileName != null && !profileName.isBlank()) names.add(profileName);
        if (shownName != null) {
            String shown = BRACKETS.matcher(FORMAT.matcher(shownName).replaceAll("")).replaceAll(" ");
            var matcher = NAME.matcher(shown);
            String best = null;
            while (matcher.find()) if (matcher.group().length() >= 3 && (best == null || matcher.group().length() > best.length())) best = matcher.group();
            if (best != null) names.add(best);
        }
        return names;
    }
}
