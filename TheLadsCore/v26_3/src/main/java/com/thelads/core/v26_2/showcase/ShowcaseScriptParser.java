package com.thelads.core.v26_2.showcase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShowcaseScriptParser {
    public record Command(int lineNo, String verb, String[] args, String rawLine) {
        public String arg(int index, String def) {
            return args.length > index ? args[index] : def;
        }

        public int intArg(int index, int def) {
            if (args.length <= index) return def;
            try { return Integer.parseInt(args[index]); } catch (Exception e) { return def; }
        }

        public long longArg(int index, long def) {
            if (args.length <= index) return def;
            try { return Long.parseLong(args[index]); } catch (Exception e) { return def; }
        }

        public double doubleArg(int index, double def) {
            if (args.length <= index) return def;
            try { return Double.parseDouble(args[index]); } catch (Exception e) { return def; }
        }

        public String tail(int fromIndex) {
            if (args.length <= fromIndex) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = fromIndex; i < args.length; i++) {
                if (i > fromIndex) sb.append(' ');
                sb.append(args[i]);
            }
            return sb.toString();
        }
    }

    // A token is a bare word, or prefix"quoted words" ("Wavy Capes", @"Video Settings"): the quotes keep the spaces.
    private static final Pattern TOKEN = Pattern.compile("([^\\s\"]*)\"([^\"]*)\"|(\\S+)");

    private ShowcaseScriptParser() {}

    public static Command parseLine(String line, int lineNo) {
        if (line == null) return null;
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null;

        // Strip inline comments if separated by whitespace
        int hashIdx = trimmed.indexOf(" #");
        if (hashIdx >= 0) {
            trimmed = trimmed.substring(0, hashIdx).trim();
        }

        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(trimmed);
        while (m.find()) tokens.add(m.group(3) != null ? m.group(3) : m.group(1) + m.group(2));
        String[] parts = tokens.toArray(new String[0]);
        if (parts.length == 0 || parts[0].isEmpty()) return null;

        String verb = parts[0].toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        return new Command(lineNo, verb, args, trimmed);
    }

    public static List<Command> parseLines(List<String> lines) {
        List<Command> commands = new ArrayList<>();
        if (lines == null) return commands;
        for (int i = 0; i < lines.size(); i++) {
            Command cmd = parseLine(lines.get(i), i + 1);
            if (cmd != null) commands.add(cmd);
        }
        return commands;
    }
}
