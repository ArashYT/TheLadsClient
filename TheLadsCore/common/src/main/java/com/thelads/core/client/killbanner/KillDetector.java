package com.thelads.core.client.killbanner;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Which deaths the client sees are the local player's kills, as they happen (no server statistics round trip):
 * <ul>
 * <li>the death event (or zero health) of an entity the local player damaged last, within 5 seconds, as vanilla credits a kill;</li>
 * <li>a server kill message that names the local player as the killer ("Bob was killed by Me.", "You killed Bob", "KILL! on Bob"),
 * as plugin minigames (Hypixel) send instead of real deaths: of a player the local player hit within 10 seconds, or of any
 * player in the world or the tab list when the line plainly reports a kill, or is shaped like one (cosmetic kill messages:
 * "Bob was spooked by Me.", "Bob lost the draw to Me."; 1.8.9 sends no damage events, so arrows, rods and fireballs that
 * knock someone into the void leave no hit). Vanilla death messages go by their translation key ({@link #deathLine}), so any
 * client language counts.</li>
 * <li>a reward line only the killer gets ("+10 coins! (Kill)", "+20 coins! (Final Kill)"), for a kill whose message said
 * nothing the lines above know.</li>
 * </ul>
 * Each death counts once, whichever signal arrives first: the same player's death again (another record of the same name
 * included) needs a new hit first (respawned, or the same entity on minigame servers), and a reward line within 3 seconds of
 * a player kill is that kill's, as the kill is a reward line's that came first.
 */
public final class KillDetector {
    public enum Kind { PLAYER, MOB, BOSS }
    public record Kill(Kind kind, String victim, boolean headshot) {}

    static final long CREDIT = 5_000_000_000L, CHAT = 10_000_000_000L, ONCE = 5_000_000_000L, FORGET = 30_000_000_000L;
    static final long PAIR = 3_000_000_000L, BURST = 1_000_000_000L;
    private static final Pattern FORMAT = Pattern.compile("§.");
    private static final Pattern BRACKETS = Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    // Words that tie a victim to their killer in kill messages, themed ones included ("Bob was spooked by Me").
    // "Bob lost the draw to Me", "Bob fell to the superior marksmanship of Me", "Bob disconnected while fighting with Me".
    private static final Pattern LINK = Pattern.compile("\\b(by|whilst|while|fighting|escape|escaping|against|hurt|due to|world as|from|to|of|with|for)\\b");
    private static final Pattern VERB = Pattern.compile("\\b(killed|slain|slew|eliminated|finished|destroyed|defeated|murdered|knocked|shot)\\b");
    private static final Pattern PASSIVE = Pattern.compile("\\b(by|was|were|got)\\b");
    // The Pit: "KILL! on [120] Bob", "DOUBLE KILL! on [120] Bob", "MULTI KILL! (6) on [120] Bob"
    private static final Pattern KILL_TAG = Pattern.compile("^\\W*((double|triple|quad|penta|multi)\\s+)?kill!?\\s*(\\(\\d+\\)\\s*)?(on\\s+)?(\\[[^\\]]*\\]\\s*)*$");
    // Lines about players that are no kill (nor the player's death) whatever their shape: guild, party and friend notices, tips
    // ("You were tipped by 5 players in the last minute!"), revives, a bed destroyed.
    private static final Pattern SOCIAL = Pattern.compile("\\b(guild|party|partied|friend|friends|invited|kicked|promoted|demoted|muted"
        + "|transferred|request|gift|gifted|tipped|revived|healed|saved|rescued|cured|found|teleported|warped|summoned|joined|lobby|destruction)\\b");
    // What may follow the killer in a cosmetic kill message: "'s Iron Golem", "with AK-47", "using [Bow]", "FINAL KILL!".
    private static final Pattern TAIL = Pattern.compile("^('s\\b[^.!:]{0,32})?( with [^.!:]{1,32})?( using)?[^a-z0-9]*(final kill[^a-z0-9]*)?$");
    // Hypixel's reward lines, sent to the killer alone: "+10 coins! (Kill)", "+20 coins! (Bob's Network Booster) (Final Kill)",
    // "+25 Bed Wars Experience (Final Kill)"; group 1 names the reward, as one kill may bring a line of each.
    private static final Pattern REWARD = Pattern.compile("^\\s*\\+\\s?[\\d,.]+\\s+([a-z]+)[^:]*\\((?:[a-z]+ )?kill\\)");
    // A kill message about a player the client saw no hit on must say so in plain words.
    private static final Pattern PLAIN_KILL = Pattern.compile("\\b(kill|killed|slain|slew|shot|knocked|thrown|void|fireballed|pummeled|blown|doomed"
        + "|finished|eliminated|murdered|impaled|smashed|whilst|escape|fighting)\\b");
    // The local player's own death, after their name at the line's start: "Me was killed by Bob.", "Me fell into the void.",
    // "You died!", and The Pit's "DEATH! by Bob".
    private static final Pattern DIED = Pattern.compile("^\\s+(((was|were|has been|got)\\b.{0,60}\\b(by|whilst|fighting|escape|escaping|against|due to|world as|from)\\b)"
        + "|((was|were|has been|got) (killed|slain|shot|squashed|squished|blown up|pricked|poked|impaled|struck|roasted|obliterated|frozen"
        + "|stung|skewered|doomed|fireballed|pummeled|eliminated|burnt|burned)\\b)"
        + "|((died|fell|lost|drowned|burned|burnt|blew up|hit the ground|tried to swim|walked into|went up in flames|went off|starved"
        + "|suffocated|froze|withered|experienced|discovered|didn't want|left the confines)\\b))");
    private static final Pattern DEATH_TAG = Pattern.compile("^\\W*death!");
    // Symbols and bracketed tags before the first name of a line: "[MVP+] ", "» ", "(Lv 3) ".
    private static final Pattern LEAD = Pattern.compile("^([^a-z0-9_\\[(]*(\\[[^\\]]*\\]|\\([^)]*\\)))*[^a-z0-9_]*");

    private static final class Victim {
        final Set<String> names = new LinkedHashSet<>();
        Kind kind;
        long mine = Long.MIN_VALUE, other = Long.MIN_VALUE, killed = Long.MIN_VALUE;
        boolean head;
    }
    private final Map<Integer, Victim> byId = new HashMap<>();
    /** Player kills counted in the last {@link #PAIR} still waiting for their reward line; reward kills still waiting for their message. */
    private final Deque<Long> unrewarded = new ArrayDeque<>(), unnamed = new ArrayDeque<>();
    /** The reward lines arriving together (one kill's, or a few kills' at once): each kind's count, and the kills they made. */
    private final Map<String, Integer> burst = new HashMap<>();
    private long burstAt = Long.MIN_VALUE;
    private int burstKills;

    public void reset() {
        byId.clear();
        unrewarded.clear();
        unnamed.clear();
        burst.clear();
        burstKills = 0;
        burstAt = Long.MIN_VALUE;
    }

    /**
     * Did a hit coming from ({@code fromX}, {@code fromY}, {@code fromZ}) (the attacker's eyes, an arrow's last place) through
     * ({@code x}, {@code y}, {@code z}) (the crosshair's point on the target, where an arrow crossed it) land in the head of an
     * entity with the box {@code minX}..{@code maxZ}, its eyes at {@code eyeY} and its body turned to {@code yaw} (degrees, 0 =
     * south)? The head is as tall as a quarter of the body, or half its thickness for a stocky one, never more than the box is
     * wide or tall, and reaches from the eyes up to three quarters of its size above them (the top of the box for nearly every mob:
     * players' top quarter, as before). An upright body (1.75 times as tall as wide or more: players, zombies, endermen) wears it on
     * top, all round; any other (animals, spiders, wide or flat modded mobs) at its front, so its back, rump and sides are body even
     * up top. Anything odd (no box) is no head hit.
     */
    public static boolean headHit(double fromX, double fromY, double fromZ, double x, double y, double z,
                                  double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double eyeY, float yaw) {
        double width = Math.min(maxX - minX, maxZ - minZ), height = maxY - minY;
        if (!(width > 0 && height > 0) || Double.isNaN(fromX + fromY + fromZ + x + y + z + eyeY)) return false;
        // The point is on a grown box (1.8.9's crosshair 0.1, arrows 0.3), so a shallow hit lands too far forward on it: where the
        // same line enters the box itself is where it hit (the origin when inside it). A line that only grazed the grown box, and
        // a point with no line (the crosshair's eye inside the box), stay where they are, moved onto the box.
        double[] from = {fromX, fromY, fromZ}, along = {x - fromX, y - fromY, z - fromZ}, low = {minX, minY, minZ}, high = {maxX, maxY, maxZ};
        double enter = 0, leave = Double.MAX_VALUE;
        for (int axis = 0; axis < 3 && enter <= leave; axis++) {
            if (Math.abs(along[axis]) < 1e-9) {
                if (from[axis] < low[axis] || from[axis] > high[axis]) enter = Double.POSITIVE_INFINITY;
                continue;
            }
            double a = (low[axis] - from[axis]) / along[axis], b = (high[axis] - from[axis]) / along[axis];
            enter = Math.max(enter, Math.min(a, b));
            leave = Math.min(leave, Math.max(a, b));
        }
        if (enter <= leave) {
            x = fromX + along[0] * enter;
            y = fromY + along[1] * enter;
            z = fromZ + along[2] * enter;
        }
        x = Math.max(minX, Math.min(maxX, x));
        y = Math.max(minY, Math.min(maxY, y));
        z = Math.max(minZ, Math.min(maxZ, z));
        double head = Math.min(Math.max(height / 4, Math.min(width, height) / 2), Math.min(width, height));
        double top = Math.min(maxY, Math.max(minY + head, eyeY + head * .75));
        if (y < top - head || y > top) return false;
        if (height >= width * 1.75) return true;
        double radians = Math.toRadians(yaw), forwardX = -Math.sin(radians), forwardZ = Math.cos(radians);
        double dx = x - (minX + maxX) / 2, dz = z - (minZ + maxZ) / 2;
        return dx * forwardX + dz * forwardZ >= width / 2 - head && Math.abs(dx * forwardZ - dz * forwardX) <= head / 2;
    }

    /** A multipart mob's part by its name (the dragon's "head", a modded mob's {@code headPart}): only the head is a head hit. */
    public static boolean headPart(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains("head");
    }

    /** Is {@code entity} a part of a multipart mob? Forge's PartEntity and modded segments have a public getParent(). */
    public static boolean part(Object entity) {
        try {
            entity.getClass().getMethod("getParent");
            return true;
        } catch (NoSuchMethodException | RuntimeException | LinkageError none) {
            return false;
        }
    }

    /** The mob a part belongs to (its getParent()), or null: no part, or a segment whose client copy does not know its mob. */
    public static Object parentOf(Object part) {
        try {
            Object parent = part.getClass().getMethod("getParent").invoke(part);
            return parent != part ? parent : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError none) {
            return null;
        }
    }

    /** The name of the public field of {@code parent} holding {@code part} (Alex's Mobs and Caves: headPart, tail1Part), else "". */
    public static String partName(Object parent, Object part) {
        if (parent == null || part == null) return "";
        for (java.lang.reflect.Field field : parent.getClass().getFields()) {
            try {
                if (field.get(parent) == part) return field.getName();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError skipped) {
                // not readable: not this one
            }
        }
        return "";
    }

    /** The local player hit {@code id} and saw where (an attack, an arrow): {@code head} is that hit's verdict, whatever came before. */
    public void hitByMe(int id, Collection<String> names, Kind kind, boolean head, long now) {
        forget(now);
        Victim victim = byId.computeIfAbsent(id, key -> new Victim());
        for (String name : names) if (name != null && !name.isBlank()) victim.names.add(name.toLowerCase(Locale.ROOT));
        victim.kind = kind;
        victim.head = head;
        victim.mine = now;
    }

    /**
     * A damage event names the local player as the cause of {@code id}'s hurt: it confirms the hit seen a moment ago (and keeps its
     * verdict), or is a hit of its own, not in the head, when none was (it does not know where it landed).
     */
    public void damagedByMe(int id, Collection<String> names, Kind kind, long now) {
        Victim victim = byId.get(id);
        boolean head = victim != null && victim.head && since(now, victim.mine) <= 1_000_000_000L;
        hitByMe(id, names, kind, head, now);
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

    /** A server (system) chat line, with no other players known; player chat never counts. */
    public Kill chat(String message, Collection<String> myNames, long now) {
        return chat(message, myNames, Map::of, now);
    }

    /**
     * A server (system) chat line; player chat never counts. {@code players}: the other players in the world, entity id to
     * names ({@link #names}), asked for only when no player the local player hit fits the line.
     */
    public Kill chat(String message, Collection<String> myNames, Supplier<? extends Map<Integer, ? extends Collection<String>>> players, long now) {
        if (message == null || message.isBlank()) return null;
        String text = strip(message);
        var reward = REWARD.matcher(text);
        if (reward.find()) return reward(reward.group(1), now);
        if (SOCIAL.matcher(text).find()) return null;
        Set<String> me = lower(myNames);
        for (Victim victim : byId.values()) {
            if (victim.kind != Kind.PLAYER || since(now, victim.mine) > CHAT || !named(text, victim.names, me)) continue;
            Kill kill = claim(victim, now); // null: this death was already counted; another record may share the name
            if (kill != null) return kill;
        }
        boolean plain = PLAIN_KILL.matcher(text).find();
        for (Map.Entry<Integer, ? extends Collection<String>> player : players.get().entrySet()) {
            Set<String> names = lower(player.getValue());
            if (!named(text, names, me) || !plain && !themed(text, names, me)) continue;
            Victim victim = byId.computeIfAbsent(player.getKey(), id -> new Victim());
            victim.names.addAll(names);
            victim.kind = Kind.PLAYER;
            Kill kill = claim(victim, now);
            if (kill != null) return kill;
        }
        return null;
    }

    /** A server chat line reporting the local player's own death (servers that never really kill anyone send only this). */
    public static boolean myDeath(String message, Collection<String> myNames) {
        if (message == null || message.isBlank()) return false;
        String text = strip(message);
        if (DEATH_TAG.matcher(text).find()) return true;
        if (SOCIAL.matcher(text).find()) return false;
        var lead = LEAD.matcher(text);
        String rest = lead.lookingAt() ? text.substring(lead.end()) : text;
        Set<String> me = lower(myNames);
        me.add("you");
        for (String name : me) {
            if (word(rest, name, 0) != 0) continue;
            String after = BRACKETS.matcher(rest.substring(name.length())).replaceAll(" ");
            if (DIED.matcher(after).find()) return true;
        }
        return false;
    }

    /**
     * A vanilla death message ("death.attack.player" and the rest: the victim, then the killer if any) as the English line the
     * rest of the detector reads, so a client in any language counts it; null for any other message.
     */
    public static String deathLine(String key, List<String> args) {
        if (key == null || !key.startsWith("death.") || args.isEmpty()) return null;
        return args.size() > 1 ? args.get(0) + " was slain by " + args.get(1) : args.get(0) + " died";
    }

    private static String strip(String message) { return FORMAT.matcher(message).replaceAll("").toLowerCase(Locale.ROOT); }

    private static Set<String> lower(Collection<String> names) {
        Set<String> lower = new LinkedHashSet<>();
        for (String name : names) if (name != null && !name.isBlank()) lower.add(name.toLowerCase(Locale.ROOT));
        return lower;
    }

    /** Does {@code text} report one of {@code names} killed by one of {@code me}? */
    private static boolean named(String text, Set<String> names, Set<String> me) {
        for (String name : names) if (!me.contains(name) && killMessage(text, name, me)) return true;
        return false;
    }

    private Kill claim(Victim victim, long now) {
        // The death event, a zero health update and the kill message all report one death, with no hit in between; a record of
        // the same name (the player's entity and their tab-list entry, a respawned entity) too.
        for (Victim other : byId.values())
            if ((other == victim || !Collections.disjoint(other.names, victim.names)) && since(now, other.killed) < ONCE && other.killed >= victim.mine) return null;
        victim.killed = now;
        if (victim.kind == Kind.PLAYER) {
            prune(now);
            if (unnamed.pollFirst() != null) return null; // its reward line came first and showed the banner
            unrewarded.addLast(now);
        }
        return new Kill(victim.kind, victim.names.isEmpty() ? "" : victim.names.iterator().next(), victim.head);
    }

    /**
     * A reward line of kind {@code kind}: lines within a second of each other are the same kills' (a kill may bring coins and
     * experience), so a kill is each line of a kind past the kills counted so far. A kill a message or death already counted
     * keeps its banner; otherwise this is a player kill of its own.
     */
    private Kill reward(String kind, long now) {
        if (since(now, burstAt) > BURST) { burst.clear(); burstKills = 0; }
        burstAt = now;
        int lines = burst.merge(kind, 1, Integer::sum);
        if (lines <= burstKills) return null;
        burstKills = lines;
        prune(now);
        if (unrewarded.pollFirst() != null) return null;
        unnamed.addLast(now);
        return new Kill(Kind.PLAYER, "", false);
    }

    private void prune(long now) {
        while (!unrewarded.isEmpty() && now - unrewarded.peekFirst() > PAIR) unrewarded.pollFirst();
        while (!unnamed.isEmpty() && now - unnamed.peekFirst() > PAIR) unnamed.pollFirst();
    }

    /** Nanoseconds since {@code then}; never for an event that has not happened. */
    private static long since(long now, long then) { return then == Long.MIN_VALUE ? Long.MAX_VALUE : now - then; }

    private void forget(long now) {
        for (Iterator<Victim> it = byId.values().iterator(); it.hasNext(); ) {
            Victim victim = it.next();
            if (since(now, victim.mine) > FORGET && since(now, victim.killed) > FORGET) it.remove();
        }
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
            if (m > 0 && linked(text.substring(v + victim.length(), m))) return true;
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

    /** The words between a victim and the local player tie them as victim and killer: the killer right after "by", "fighting"... */
    private static boolean linked(String between) {
        if (BRACKETS.matcher(between).replaceAll("").length() > 64 || between.contains(":") || between.contains("assist")) return false;
        // The killer named right after "by" / "fighting" is the local player, not someone else first.
        var link = LINK.matcher(between);
        int end = -1;
        while (link.find()) end = link.end();
        return end >= 0 && !BRACKETS.matcher(between.substring(end)).replaceAll("").matches(".*[a-z0-9].*");
    }

    /**
     * Lower-case {@code text}, about a player the client saw no hit on, in a cosmetic kill message's shape: it starts with one
     * of {@code names} (after ranks and tags), ends with one of {@code me} after a linking word, and nothing but a possessive,
     * weapon or "FINAL KILL!" follows ("Bob was spooked by Me.", "[RED] Bob lost the draw to [BLUE] Me. FINAL KILL!").
     */
    static boolean themed(String text, Set<String> names, Set<String> me) {
        var lead = LEAD.matcher(text);
        String rest = lead.lookingAt() ? text.substring(lead.end()) : text;
        for (String victim : names) {
            if (me.contains(victim) || word(rest, victim, 0) != 0) continue;
            for (String name : me)
                for (int m = word(rest, name, victim.length()); m > 0; m = word(rest, name, m + 1))
                    if (linked(rest.substring(victim.length(), m))
                        && TAIL.matcher(BRACKETS.matcher(rest.substring(m + name.length())).replaceAll(" ")).matches()) return true;
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

    /** The key of a tab-list player the client's world does not hold (out of sight, already gone): apart from entity ids. */
    public static int tabId(java.util.UUID player) {
        return Integer.MIN_VALUE + (player.hashCode() & 0x3FFFFFFF);
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
