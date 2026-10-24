package com.thelads.core.client;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Custom Item Textures (CIT): the OptiFine/MCPatcher format resource packs ship under {@code optifine/cit/} (or the older
 * {@code mcpatcher/cit/}), for the versions and paths that have nothing reading it (1.8.9 with Ionium; OptiFine reads it itself).
 * Each {@code .properties} file in those folders (any depth) is one rule: a type (item, enchantment or armor), the items it applies
 * to and the conditions a stack must meet (damage with mask or percent, stack size, enchantments and their levels, NBT, hand), and
 * the replacement texture(s). For one stack the first matching rule wins, ordered by weight (highest first) and then file name.
 * Clean room from OptiFine's public-domain docs (cit.properties, cit_single.properties); enchantment level matching follows
 * MCPatcher (MIT, see THIRD_PARTY_NOTICES.txt): without enchantment ids the levels of all enchantments are summed.
 * Not read: the potion file-name shortcut (cit/potion/...), {@code model.<name>}, elytra. Each version lists the files, reads them and
 * draws; this class has the format and the matching.
 */
public final class CustomItems {
    /** Where packs keep the rules (under assets/minecraft). */
    public static final String[] FOLDERS = {"optifine/cit/", "mcpatcher/cit/"};
    /** Enchantment ids a stack can carry (as MCPatcher). */
    public static final int MAX_ENCHANTMENTS = 256;
    public static final int HAND_ANY = 0, HAND_MAIN = 1, HAND_OFF = 2;

    public enum Type { ITEM, ENCHANTMENT, ARMOR }

    /** A version's registries: an item or enchantment token ("diamond_sword", "minecraft:flame", "276") to its numeric id, or -1. */
    public interface Ids {
        int item(String token);
        int enchantment(String token);
    }

    private CustomItems() {}

    /** What a rule reads from one stack. Reused per lookup (one per thread), so a lookup allocates nothing. */
    public static final class Stack {
        public int item, damage, maxDamage, count, hand = HAND_MAIN;
        /** Shows the enchantment glint (enchanted, or a golden apple, a potion ...). */
        public boolean glint;
        /** The stack's NBT root (null when it has none) and how to read it. */
        public Object nbt;
        public CustomItemsNbt.Access access;
        final int[] levels = new int[MAX_ENCHANTMENTS];
        int levelSum;
        boolean enchanted;

        public void clearEnchantments() {
            if (enchanted) java.util.Arrays.fill(levels, 0);
            enchanted = false;
            levelSum = 0;
        }

        public void addEnchantment(int id, int level) {
            if (id < 0 || id >= MAX_ENCHANTMENTS || level <= 0) return;
            levels[id] += level;
            levelSum += level;
            enchanted = true;
        }
    }

    /** One properties file. */
    public static final class Rule implements Comparable<Rule> {
        /** The file, "namespace:path". */
        public final String path;
        public final Type type;
        /** Item ids, or null for any item (enchantment rules only). */
        public final int[] items;
        /** The texture ("namespace:path.png") used where no {@link #textures} entry names the texture being replaced; may be null. */
        public final String texture;
        /** {@code texture.<name>}: the replacement for the texture called name (file name without folder or .png). */
        public final Map<String, String> textures;
        /** {@code model=}, resolved ("namespace:path.json"); parsed, not drawn yet. */
        public final String model;
        public final int weight;
        // enchantment rules
        public final CustomSky.Blend blend;
        public final float speed, rotation, duration;
        public final int layer;
        final int[] damage, stackSize, enchantmentLevels;
        final boolean damagePercent;
        final int damageMask;
        final BitSet enchantmentIds;
        final CustomItemsNbt[] nbt;
        final int hand;

        Rule(String path, Type type, int[] items, String texture, Map<String, String> textures, String model, int weight, CustomSky.Blend blend,
             float speed, float rotation, float duration, int layer, int[] damage, boolean damagePercent, int damageMask, int[] stackSize,
             BitSet enchantmentIds, int[] enchantmentLevels, CustomItemsNbt[] nbt, int hand) {
            this.path = path;
            this.type = type;
            this.items = items;
            this.texture = texture;
            this.textures = textures;
            this.model = model;
            this.weight = weight;
            this.blend = blend;
            this.speed = speed;
            this.rotation = rotation;
            this.duration = duration;
            this.layer = layer;
            this.damage = damage;
            this.damagePercent = damagePercent;
            this.damageMask = damageMask;
            this.stackSize = stackSize;
            this.enchantmentIds = enchantmentIds;
            this.enchantmentLevels = enchantmentLevels;
            this.nbt = nbt;
            this.hand = hand;
        }

        /** The replacement for the texture called {@code name} (e.g. "bow_pulling_0"), else {@link #texture} (may be null). */
        public String texture(String name) {
            String specific = name == null ? null : textures.get(name);
            return specific != null ? specific : texture;
        }

        /** Everything but the item, which the index has already checked. */
        public boolean matches(Stack s) {
            if (hand != HAND_ANY && hand != s.hand) return false;
            if (damage != null) {
                if (damagePercent) {
                    if (s.maxDamage <= 0) return false;
                    int percent = Math.max(0, Math.min(100, 100 * s.damage / s.maxDamage));
                    if (!in(damage, percent)) return false;
                } else if (!in(damage, s.damage & damageMask)) {
                    return false;
                }
            }
            if (stackSize != null && !in(stackSize, s.count)) return false;
            if ((enchantmentIds != null || enchantmentLevels != null) && !enchantments(s)) return false;
            for (CustomItemsNbt condition : nbt) if (!condition.matches(s.nbt, s.access)) return false;
            return true;
        }

        private boolean enchantments(Stack s) {
            if (!s.enchanted) return s.glint && enchantmentIds == null && in(enchantmentLevels, 1);
            if (enchantmentIds == null) return in(enchantmentLevels, s.levelSum);
            for (int id = enchantmentIds.nextSetBit(0); id >= 0; id = enchantmentIds.nextSetBit(id + 1)) {
                int level = s.levels[id];
                if (level > 0 && (enchantmentLevels == null || in(enchantmentLevels, level))) return true;
            }
            return false;
        }

        /** Highest weight first, then by file name. */
        @Override
        public int compareTo(Rule o) {
            int byWeight = Integer.compare(o.weight, weight);
            return byWeight != 0 ? byWeight : path.compareTo(o.path);
        }

        @Override
        public String toString() {
            return "CIT " + type.name().toLowerCase(Locale.ROOT) + " " + path;
        }
    }

    /** All rules of the loaded packs, indexed by item. */
    public static final class Rules {
        public static final Rules EMPTY = new Rules(Collections.<Rule>emptyList(), true);
        private static final Rule[] NONE = new Rule[0];

        private final Map<Integer, Rule[]> items, armor;
        private final List<Rule> enchantments;
        /** cit.properties' useGlint: whether the vanilla glint still draws where no enchantment rule matches. */
        public final boolean useGlint;
        public final int count;

        Rules(List<Rule> rules, boolean useGlint) {
            this.useGlint = useGlint;
            count = rules.size();
            List<Rule> sorted = new ArrayList<>(rules);
            Collections.sort(sorted);
            items = index(sorted, Type.ITEM);
            armor = index(sorted, Type.ARMOR);
            List<Rule> glints = new ArrayList<>();
            for (Rule rule : sorted) if (rule.type == Type.ENCHANTMENT) glints.add(rule);
            enchantments = Collections.unmodifiableList(glints);
        }

        private static Map<Integer, Rule[]> index(List<Rule> sorted, Type type) {
            Map<Integer, List<Rule>> byItem = new HashMap<>();
            for (Rule rule : sorted) {
                if (rule.type != type) continue;
                for (int item : rule.items) byItem.computeIfAbsent(item, k -> new ArrayList<>()).add(rule);
            }
            Map<Integer, Rule[]> result = new HashMap<>();
            for (Map.Entry<Integer, List<Rule>> e : byItem.entrySet()) result.put(e.getKey(), e.getValue().toArray(NONE));
            return result;
        }

        public boolean isEmpty() { return count == 0; }
        public boolean hasItems() { return !items.isEmpty(); }
        public boolean hasArmor() { return !armor.isEmpty(); }
        /** Whether any item or armor rule names this item: stacks of other items need no lookup at all. */
        public boolean names(int item) { return items.containsKey(item) || armor.containsKey(item); }

        /** The item rule for this stack, or null. */
        public Rule item(Stack s) { return first(items.get(s.item), s); }
        /** The armor rule for this stack, or null. */
        public Rule armor(Stack s) { return first(armor.get(s.item), s); }
        /** Enchantment rules, by weight then file name. */
        public List<Rule> enchantments() { return enchantments; }

        private static Rule first(Rule[] candidates, Stack s) {
            if (candidates == null) return null;
            for (Rule rule : candidates) if (rule.matches(s)) return rule;
            return null;
        }

        /** Every texture the item rules use ("namespace:path.png"): these go into the item atlas. */
        public Set<String> itemTextures() {
            Set<String> all = new LinkedHashSet<>();
            for (Rule[] rules : items.values()) {
                for (Rule rule : rules) {
                    if (rule.texture != null) all.add(rule.texture);
                    all.addAll(rule.textures.values());
                }
            }
            return all;
        }
    }

    /**
     * The rules in {@code files} ("namespace:path" of every .properties under {@link #FOLDERS}, from all packs). {@code read} gives a
     * file's text (null when missing), {@code exists} whether a texture is in the packs.
     */
    public static Rules load(Collection<String> files, Function<String, String> read, Predicate<String> exists, Ids ids) {
        boolean useGlint = true;
        for (String folder : FOLDERS) {
            String global = read.apply("minecraft:" + folder + "cit.properties");
            if (global == null) continue;
            Properties p = properties(global);
            if (p != null) useGlint = !"false".equalsIgnoreCase(get(p, "useGlint"));
            break;
        }
        List<Rule> rules = new ArrayList<>();
        for (String file : files) {
            if (!file.endsWith(".properties") || file.endsWith("/cit.properties")) continue;
            String text = read.apply(file);
            Rule rule = text == null ? null : parse(file, text, exists, ids);
            if (rule != null) rules.add(rule);
        }
        return new Rules(rules, useGlint);
    }

    /** One rule file ("namespace:path.properties"); null when it is broken, matches nothing or has no texture to show. */
    public static Rule parse(String path, String text, Predicate<String> exists, Ids ids) {
        Properties p = properties(text);
        int colon = path.indexOf(':'), slash = path.lastIndexOf('/');
        if (p == null || slash < colon) return null;
        String folder = path.substring(colon + 1, slash + 1);
        Type type;
        switch (lower(get(p, "type"), "item")) {
            case "item": type = Type.ITEM; break;
            case "enchantment": case "overlay": type = Type.ENCHANTMENT; break;
            case "armor": type = Type.ARMOR; break;
            default: return null; // elytra and anything newer
        }

        Map<String, String> textures = new HashMap<>();
        for (String key : p.stringPropertyNames()) {
            String name = key.startsWith("texture.") ? key.substring(8) : key.startsWith("source.") ? key.substring(7)
                : key.startsWith("tile.") ? key.substring(5) : null;
            if (name == null) continue;
            name = textureName(name);
            String texture = texturePath(get(p, key), folder);
            if (!name.isEmpty() && texture != null && exists.test(texture)) textures.put(name, texture);
        }
        String value = first(p, "texture", "source", "tile");
        String texture;
        if (value != null) {
            texture = texturePath(value, folder);
        } else if (textures.isEmpty() && first(p, "model") == null) { // the png beside the properties
            texture = path.substring(0, path.length() - ".properties".length()) + ".png";
        } else {
            texture = null;
        }
        if (texture != null && !exists.test(texture)) texture = null;
        String model = modelPath(first(p, "model"), folder);
        // ponytail: model= is parsed but not drawn yet, so a rule with no texture is dropped rather than shadowing lower rules.
        if (texture == null && textures.isEmpty()) return null;

        int[] items = null;
        String itemList = first(p, "items", "matchItems");
        if (itemList != null) {
            LinkedHashSet<Integer> found = new LinkedHashSet<>();
            for (String token : itemList.split("[\\s,]+")) {
                int id = token.isEmpty() ? -1 : ids.item(token);
                if (id >= 0) found.add(id);
            }
            if (found.isEmpty()) return null; // names only items this game does not have
            items = new int[found.size()];
            int i = 0;
            for (int id : found) items[i++] = id;
        } else if (type != Type.ENCHANTMENT) {
            return null;
        }

        String damageValue = get(p, "damage");
        boolean percent = damageValue != null && damageValue.contains("%");
        int[] damage = damageValue == null ? null : orNever(ranges(damageValue.replace("%", "")));
        int mask = integer(get(p, "damageMask"), -1);

        BitSet enchantmentIds = null;
        String enchantments = first(p, "enchantments", "enchantmentIDs");
        if (enchantments != null) {
            enchantmentIds = new BitSet();
            for (String token : enchantments.split("[\\s,]+")) {
                if (token.isEmpty()) continue;
                int[] numbers = Character.isDigit(token.charAt(0)) ? ranges(token) : null;
                if (numbers != null) {
                    for (int i = 0; i < numbers.length; i += 2)
                        enchantmentIds.set(Math.max(0, numbers[i]), (int) Math.min(MAX_ENCHANTMENTS, (long) numbers[i + 1] + 1));
                } else {
                    int id = ids.enchantment(token);
                    if (id >= 0 && id < MAX_ENCHANTMENTS) enchantmentIds.set(id);
                }
            }
        }

        List<CustomItemsNbt> nbt = new ArrayList<>();
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith("nbt.")) continue;
            CustomItemsNbt condition = CustomItemsNbt.parse(key.substring(4), get(p, key));
            if (condition == null) return null; // a broken condition must not widen the rule
            nbt.add(condition);
        }

        int hand;
        switch (lower(get(p, "hand"), "any")) {
            case "main": case "mainhand": hand = HAND_MAIN; break;
            case "off": case "offhand": hand = HAND_OFF; break;
            default: hand = HAND_ANY;
        }

        return new Rule(path, type, items, texture, textures, model, integer(get(p, "weight"), 0), CustomSky.blend(get(p, "blend")),
            number(get(p, "speed"), 0), number(get(p, "rotation"), 0), number(get(p, "duration"), 1), integer(get(p, "layer"), 0),
            damage, percent, mask, rangesOrNull(get(p, "stackSize")), enchantmentIds, rangesOrNull(get(p, "enchantmentLevels")),
            nbt.toArray(new CustomItemsNbt[0]), hand);
    }

    /** A replacement texture: "./x" and a bare name beside the properties, "~/x" under optifine/, "ns:path", or a path; adds .png. */
    static String texturePath(String value, String folder) {
        if (value == null || value.isEmpty()) return null;
        String resolved = CustomSky.texture(value, folder, 0);
        if (resolved == null) return null;
        return resolved.endsWith(".png") ? resolved : resolved + ".png";
    }

    /** A replacement model: as a texture, but "item/x" means models/item/x; adds .json. */
    static String modelPath(String value, String folder) {
        if (value == null || value.isEmpty()) return null;
        String s = value.startsWith("item/") || value.startsWith("block/") ? "models/" + value : value;
        String resolved = CustomSky.texture(s, folder, 0);
        if (resolved == null) return null;
        return resolved.endsWith(".json") ? resolved : resolved + ".json";
    }

    /** {@code texture.<name>}'s name: the file name without folders or .png ("textures/items/bow_standby.png" is "bow_standby"). */
    static String textureName(String key) {
        String name = key.substring(key.lastIndexOf('/') + 1);
        return name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
    }

    /**
     * An integer list as min/max pairs: "1 3-5 7- (-25) (-10)-10" (negative numbers in brackets; "7-" no maximum, "-5" no minimum).
     * Null when nothing in it is a number.
     */
    public static int[] ranges(String value) {
        if (value == null) return null;
        List<int[]> found = new ArrayList<>();
        for (String token : value.trim().split("[\\s,]+")) {
            int[] range = range(token);
            if (range != null) found.add(range);
        }
        if (found.isEmpty()) return null;
        int[] pairs = new int[found.size() * 2];
        for (int i = 0; i < found.size(); i++) {
            pairs[2 * i] = found.get(i)[0];
            pairs[2 * i + 1] = found.get(i)[1];
        }
        return pairs;
    }

    private static int[] rangesOrNull(String value) {
        return value == null ? null : orNever(ranges(value));
    }

    /** A list that is there but unreadable matches nothing (rather than everything). */
    private static int[] orNever(int[] ranges) {
        return ranges != null ? ranges : new int[0];
    }

    private static int[] range(String token) {
        if (token.isEmpty()) return null;
        int[] at = {0};
        Integer low = number(token, at);
        if (at[0] == token.length()) return low == null ? null : new int[]{low, low};
        if (token.charAt(at[0]) != '-') return null;
        at[0]++;
        Integer high = number(token, at);
        if (at[0] != token.length() || (low == null && high == null)) return null;
        int min = low == null ? Integer.MIN_VALUE : low, max = high == null ? Integer.MAX_VALUE : high;
        return min <= max ? new int[]{min, max} : null;
    }

    /** A number at at[0]: digits, or "(-digits)"; null when there is none. Advances at[0]. */
    private static Integer number(String s, int[] at) {
        int start = at[0];
        boolean bracket = start < s.length() && s.charAt(start) == '(';
        int i = bracket ? start + 1 : start;
        int digitsFrom = i;
        if (bracket && i < s.length() && s.charAt(i) == '-') i++;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        if (i == digitsFrom || (bracket && i == digitsFrom + 1 && s.charAt(digitsFrom) == '-')) return null;
        String digits = s.substring(digitsFrom, i);
        if (bracket) {
            if (i >= s.length() || s.charAt(i) != ')') return null;
            i++;
        }
        try {
            int n = Integer.parseInt(digits);
            at[0] = i;
            return n;
        } catch (NumberFormatException tooBig) {
            return null;
        }
    }

    public static boolean in(int[] ranges, int value) {
        for (int i = 0; i < ranges.length; i += 2) if (value >= ranges[i] && value <= ranges[i + 1]) return true;
        return false;
    }

    private static Properties properties(String text) {
        Properties p = new Properties();
        try {
            p.load(new StringReader(text));
            return p;
        } catch (IOException | IllegalArgumentException broken) {
            return null;
        }
    }

    /** The trimmed value, or null when missing or blank. */
    private static String get(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static String first(Properties p, String... keys) {
        for (String key : keys) {
            String value = get(p, key);
            if (value != null) return value;
        }
        return null;
    }

    private static String lower(String value, String fallback) {
        return value == null ? fallback : value.toLowerCase(Locale.ROOT);
    }

    private static int integer(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return value.startsWith("0x") ? (int) Long.parseLong(value.substring(2), 16) : Integer.parseInt(value);
        } catch (NumberFormatException notNumber) {
            return fallback;
        }
    }

    private static float number(String value, float fallback) {
        if (value == null) return fallback;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException notNumber) {
            return fallback;
        }
    }
}
