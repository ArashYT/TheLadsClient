package com.thelads.core.client;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One {@code nbt.<path>=<value>} condition of a Custom Item Textures rule (CustomItems), as OptiFine's public-domain docs describe it
 * (cit_single.properties): a dotted path into the item's NBT ({@code *} any compound key or list index, a number a list index,
 * {@code count} a list's size) and a value: exact, {@code pattern:}/{@code ipattern:} ({@code *} and {@code ?} wildcards),
 * {@code regex:}/{@code iregex:}, {@code range:} integers, {@code exists:true|false}, {@code raw:} (the tag's NBT text) and a leading
 * {@code !} for "not". Integers may be given as a hex colour ({@code #ff99cc}). The NBT itself is read through {@link Access}, so every
 * version can match its own tags.
 */
public final class CustomItemsNbt {
    /** Read access to one version's NBT. Nodes are that version's tag objects. */
    public interface Access {
        /** A compound's child, or null when {@code node} is not a compound or has no such key. */
        Object child(Object node, String key);
        /** A compound's keys, or null when {@code node} is not a compound. */
        Collection<String> keys(Object node);
        /** A list's size, or -1 when {@code node} is not a list. */
        int size(Object node);
        /** A list's element. */
        Object element(Object node, int index);
        /** A string tag's String, a number tag's boxed number (Byte, Short, Integer, Long, Float, Double), else null. */
        Object value(Object node);
        /** The tag as NBT text. */
        String raw(Object node);
    }

    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static final int EXACT = 0, PATTERN = 1, IPATTERN = 2, REGEX = 3, IREGEX = 4, RANGE = 5, EXISTS = 6;
    /** The most of a text a regex reads: names and lore lines are far shorter, and a runaway pattern has less to chew on. */
    static final int MAX_TEXT = 1024;
    /**
     * Characters one regex evaluation may read (about a millisecond). A pack's pattern that backtracks without end (e.g. (a+)+$ on
     * "aaaa...b") would otherwise freeze the render thread; past this it is a no match and the condition is off (see {@link #off}).
     */
    static final int REGEX_BUDGET = 1_000_000;

    private final String[] path;
    private final int kind;
    private final boolean negative, raw;
    private final String value;
    private final Pattern regex;
    private final int[] range;
    /** A regex here ran past {@link #REGEX_BUDGET}: the rule never matches again until resources reload (a new condition). */
    private volatile boolean off;

    private CustomItemsNbt(String[] path, int kind, boolean negative, boolean raw, String value, Pattern regex, int[] range) {
        this.path = path;
        this.kind = kind;
        this.negative = negative;
        this.raw = raw;
        this.value = value;
        this.regex = regex;
        this.range = range;
    }

    /** {@code path} is the key after "nbt." (e.g. "display.Name"); null when the path or the value is unusable. */
    public static CustomItemsNbt parse(String path, String value) {
        if (path == null || path.isEmpty() || value == null) return null;
        String[] segments = path.split("\\.");
        for (String segment : segments) if (segment.isEmpty()) return null;
        String v = value;
        boolean negative = false, raw = false;
        if (v.startsWith("!")) {
            negative = true;
            v = v.substring(1);
        }
        if (v.startsWith("raw:")) {
            raw = true;
            v = v.substring(4);
        }
        int kind = EXACT;
        Pattern regex = null;
        int[] range = null;
        try {
            if (v.startsWith("pattern:")) {
                kind = PATTERN;
                v = v.substring(8);
            } else if (v.startsWith("ipattern:")) {
                kind = IPATTERN;
                v = v.substring(9).toLowerCase(Locale.ROOT);
            } else if (v.startsWith("regex:")) {
                kind = REGEX;
                v = v.substring(6);
                regex = Pattern.compile(v);
            } else if (v.startsWith("iregex:")) {
                kind = IREGEX;
                v = v.substring(7);
                regex = Pattern.compile(v, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } else if (v.startsWith("range:")) {
                kind = RANGE;
                range = CustomItems.ranges(v.substring(6));
                if (range == null) return null;
            } else if (v.startsWith("exists:")) {
                kind = EXISTS;
                String flag = v.substring(7).trim();
                if (!flag.equalsIgnoreCase("true") && !flag.equalsIgnoreCase("false")) return null;
                if (flag.equalsIgnoreCase("false")) negative = !negative;
            }
        } catch (PatternSyntaxException broken) {
            return null;
        }
        return new CustomItemsNbt(segments, kind, negative, raw, v, regex, range);
    }

    /** Whether the item's NBT root (null when it has none) passes. */
    public boolean matches(Object root, Access access) {
        if (off) return false;
        boolean found = root != null && find(root, 0, access);
        return !off && found != negative;
    }

    private boolean find(Object node, int i, Access access) {
        if (i == path.length) return test(node, access);
        String segment = path[i];
        int size = access.size(node);
        if (segment.equals("*")) {
            Collection<String> keys = access.keys(node);
            if (keys != null) {
                for (String key : keys) if (find(access.child(node, key), i + 1, access)) return true;
            } else {
                for (int index = 0; index < size; index++) if (find(access.element(node, index), i + 1, access)) return true;
            }
            return false;
        }
        if (size >= 0) {
            if (segment.equals("count") && i == path.length - 1) return testValue(Integer.valueOf(size), Integer.toString(size));
            int index;
            try {
                index = Integer.parseInt(segment);
            } catch (NumberFormatException notIndex) {
                return false;
            }
            return index >= 0 && index < size && find(access.element(node, index), i + 1, access);
        }
        Object child = access.child(node, segment);
        return child != null && find(child, i + 1, access);
    }

    private boolean test(Object node, Access access) {
        if (kind == EXISTS) return true;
        if (raw) return testValue(null, access.raw(node));
        Object leaf = access.value(node);
        return testValue(leaf, leaf == null ? access.raw(node) : String.valueOf(leaf));
    }

    /** {@code number} is the leaf when it is a number (null for strings and raw text), {@code text} its text. */
    private boolean testValue(Object number, String text) {
        switch (kind) {
            case PATTERN: return glob(value, text);
            case IPATTERN: return glob(value, text.toLowerCase(Locale.ROOT));
            case REGEX:
            case IREGEX: return regex(text);
            case RANGE:
                if (!(number instanceof Number) || number instanceof Float || number instanceof Double) return false;
                long n = ((Number) number).longValue();
                return n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE && CustomItems.in(range, (int) n);
            default:
                if (value.equals(text)) return true;
                if (!(number instanceof Number)) return false;
                Number leaf = (Number) number;
                if (value.startsWith("#") && leaf instanceof Integer) {
                    try {
                        return Integer.parseInt(value.substring(1), 16) == leaf.intValue();
                    } catch (NumberFormatException notColor) {
                        return false;
                    }
                }
                try { // "1" for a double 1.0, "5" for a byte 5b
                    return Double.parseDouble(value) == leaf.doubleValue();
                } catch (NumberFormatException notNumber) {
                    return false;
                }
        }
    }

    /** The regex against at most {@link #MAX_TEXT} characters, within {@link #REGEX_BUDGET}; a runaway turns this condition off. */
    private boolean regex(String text) {
        if (off) return false;
        try {
            return regex.matcher(new Budgeted(text)).matches();
        } catch (Budgeted.Exceeded runaway) {
            off = true;
            LOG.warn("Custom Item Textures: nbt.{} regex '{}' ran too long on an item's text; that rule is off until resources reload",
                String.join(".", path), regex.pattern());
            return false;
        }
    }

    /** A text a regex reads, cut at {@link #MAX_TEXT}, that gives up once {@link #REGEX_BUDGET} characters have been read. */
    static final class Budgeted implements CharSequence {
        /** Thrown through the matcher (no stack trace, one instance: nothing allocated per runaway). */
        static final class Exceeded extends RuntimeException {
            static final Exceeded INSTANCE = new Exceeded();

            private Exceeded() {
                super("regex budget exceeded", null, false, false);
            }
        }

        private final String text;
        private final int length;
        private int left = REGEX_BUDGET;

        Budgeted(String text) {
            this.text = text;
            length = Math.min(text.length(), MAX_TEXT);
        }

        @Override public int length() { return length; }

        @Override public char charAt(int index) {
            if (--left < 0) throw Exceeded.INSTANCE;
            return text.charAt(index);
        }

        @Override public CharSequence subSequence(int start, int end) { return text.substring(start, end); }
        @Override public String toString() { return text.substring(0, length); }
    }

    /** {@code *} any run of characters, {@code ?} one character, the rest literal; the whole text must match. */
    static boolean glob(String mask, String text) {
        int m = 0, t = 0, star = -1, resume = 0;
        while (t < text.length()) {
            if (m < mask.length() && (mask.charAt(m) == '?' || (mask.charAt(m) != '*' && mask.charAt(m) == text.charAt(t)))) {
                m++;
                t++;
            } else if (m < mask.length() && mask.charAt(m) == '*') {
                star = m++;
                resume = t;
            } else if (star >= 0) {
                m = star + 1;
                t = ++resume;
            } else {
                return false;
            }
        }
        while (m < mask.length() && mask.charAt(m) == '*') m++;
        return m == mask.length();
    }
}
