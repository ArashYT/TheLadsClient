package com.thelads.ladsapi.rules;

import com.thelads.ladsapi.schema.LadsSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Generates and regenerates rules.yml from ladsapi-schema.json.
 * Regenerating adds missing entries and never changes values or comments the owner set.
 */
public final class RulesGenerator {
    private RulesGenerator() {}

    public static String generateRulesContent(LadsSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("# LadsClientAPI rules.yml (auto-generated from schema)\n");
        sb.append("# Blank module state = player's own (inherit). Options left blank = player's own.\n");
        sb.append("# State options: allow | block | \"on\"   (\"on\" should be quoted in YAML; forced on, only for server-controllable modules)\n");
        sb.append("# Option rules: { value: ..., min: ..., max: ..., choices: [...], lock: true/false }\n\n");
        sb.append("modules:\n");

        if (schema != null && schema.getModules() != null) {
            for (LadsSchema.SchemaModule mod : schema.getModules()) {
                sb.append("  ").append(yamlKey(mod.getName())).append(":\n");
                sb.append("    state:                     # allow | block | \"on\"   (blank = inherit)\n");
                if (mod.getOptions() != null) {
                    for (LadsSchema.SchemaOption opt : mod.getOptions()) {
                        String optName = opt.getName();
                        sb.append(formatOptionLine("    ", optName, opt.formatComment())).append("\n");
                    }
                }
            }
        }

        sb.append("worlds:                        # per-world overrides on top of the above\n");
        sb.append("groups:                        # players with permission ladsapi.group.<name>\n");
        return sb.toString();
    }

    public static void regenerate(Path rulesFile, LadsSchema schema, Logger logger) throws IOException {
        if (!Files.exists(rulesFile)) {
            if (rulesFile.getParent() != null && !Files.exists(rulesFile.getParent())) {
                Files.createDirectories(rulesFile.getParent());
            }
            Files.writeString(rulesFile, generateRulesContent(schema), StandardCharsets.UTF_8);
            return;
        }

        List<String> lines = Files.readAllLines(rulesFile, StandardCharsets.UTF_8);
        List<String> updated = updateRulesLines(lines, schema, logger);
        if (!updated.equals(lines)) {
            Path bakFile = rulesFile.resolveSibling(rulesFile.getFileName().toString() + ".bak");
            Files.copy(rulesFile, bakFile, StandardCopyOption.REPLACE_EXISTING);

            Path tempFile = Files.createTempFile(rulesFile.getParent(), "rules", ".tmp");
            try {
                Files.write(tempFile, updated, StandardCharsets.UTF_8);
                try {
                    Files.move(tempFile, rulesFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, rulesFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }

            if (logger != null) {
                logger.info("[LadsClientAPI] rules.yml regenerated with missing schema entries.");
            }
        }
    }

    public static List<String> updateRulesLines(List<String> existingLines, LadsSchema schema) {
        return updateRulesLines(existingLines, schema, null);
    }

    public static List<String> updateRulesLines(List<String> existingLines, LadsSchema schema, Logger logger) {
        if (schema == null || schema.getModules() == null || schema.getModules().isEmpty()) {
            return new ArrayList<>(existingLines);
        }

        // Parse with SnakeYAML first to verify valid structure
        Map<?, ?> parsedRoot = null;
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(String.join("\n", existingLines));
            if (loaded != null && !(loaded instanceof Map<?, ?>)) {
                return new ArrayList<>(existingLines);
            }
            if (loaded instanceof Map<?, ?> map) {
                parsedRoot = map;
            }
        } catch (Exception e) {
            return new ArrayList<>(existingLines);
        }

        List<String> result = new ArrayList<>(existingLines);

        // Find top-level "modules:" line index matching ^modules:\s*(#.*)?$
        int modulesIdx = -1;
        for (int i = 0; i < result.size(); i++) {
            String line = result.get(i);
            if (!line.startsWith(" ") && !line.startsWith("\t")) {
                String trimmed = line.trim();
                if (trimmed.matches("^modules:\\s*(#.*)?$")) {
                    modulesIdx = i;
                    break;
                }
            }
        }

        if (modulesIdx == -1) {
            if (parsedRoot != null && parsedRoot.containsKey("modules")) {
                String warnMsg = "[LadsClientAPI] rules.yml contains 'modules' mapping in non-standard format; leaving file unchanged.";
                if (logger != null) {
                    logger.warning(warnMsg);
                } else {
                    Logger.getLogger("LadsClientAPI").warning(warnMsg);
                }
                return new ArrayList<>(existingLines);
            }
            // No modules: section found; insert before worlds/groups or at end without destroying existing file
            int insertPos = result.size();
            for (int i = 0; i < result.size(); i++) {
                String line = result.get(i);
                if (!line.startsWith(" ") && !line.startsWith("\t")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("worlds:") || trimmed.startsWith("groups:")) {
                        insertPos = i;
                        break;
                    }
                }
            }
            result.add(insertPos, "modules:");
            modulesIdx = insertPos;
        }

        // Find the end of modules section (next top-level section: worlds, groups, etc.)
        int modulesEndIdx = result.size();
        for (int i = modulesIdx + 1; i < result.size(); i++) {
            String line = result.get(i);
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.trim().startsWith("#")) {
                modulesEndIdx = i;
                break;
            }
        }

        // Detect module and option indent from existing lines under modules:
        boolean foundModIndent = false;
        int modIndentLen = 2;
        int optIndentLen = -1;
        for (int i = modulesIdx + 1; i < modulesEndIdx; i++) {
            String line = result.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int spaces = countLeadingSpaces(line);
            if (spaces > 0) {
                if (!foundModIndent) {
                    modIndentLen = spaces;
                    foundModIndent = true;
                } else if (spaces > modIndentLen) {
                    optIndentLen = spaces;
                    break;
                }
            }
        }
        if (optIndentLen <= modIndentLen) {
            optIndentLen = modIndentLen + (modIndentLen == 2 ? 2 : modIndentLen);
        }
        String modIndent = " ".repeat(modIndentLen);
        String optIndent = " ".repeat(optIndentLen);

        for (LadsSchema.SchemaModule mod : schema.getModules()) {
            String modName = mod.getName();
            int modLineIdx = findModuleLine(result, modulesIdx + 1, modulesEndIdx, modIndentLen, modName);

            if (modLineIdx == -1) {
                // Entire module is missing: insert before modulesEndIdx
                List<String> modBlock = new ArrayList<>();
                modBlock.add(modIndent + yamlKey(modName) + ":");
                modBlock.add(optIndent + "state:                     # allow | block | \"on\"   (blank = inherit)");
                if (mod.getOptions() != null) {
                    for (LadsSchema.SchemaOption opt : mod.getOptions()) {
                        modBlock.add(formatOptionLine(optIndent, opt.getName(), opt.formatComment()));
                    }
                }
                result.addAll(modulesEndIdx, modBlock);
                modulesEndIdx += modBlock.size();
            } else {
                // Module exists: check if it is a block mapping (not shorthand or flow-style)
                if (!isBlockMappingModule(result.get(modLineIdx))) {
                    continue;
                }

                int nextModOrSection = findNextModuleOrSection(result, modLineIdx + 1, modulesEndIdx, modIndentLen);
                Set<String> existingOptions = extractExistingOptionNames(result, modLineIdx + 1, nextModOrSection);

                if (mod.getOptions() != null) {
                    List<String> toInsert = new ArrayList<>();
                    for (LadsSchema.SchemaOption opt : mod.getOptions()) {
                        if (!existingOptions.contains(opt.getName().toLowerCase())) {
                            toInsert.add(formatOptionLine(optIndent, opt.getName(), opt.formatComment()));
                        }
                    }
                    if (!toInsert.isEmpty()) {
                        result.addAll(nextModOrSection, toInsert);
                        modulesEndIdx += toInsert.size();
                    }
                }
            }
        }

        // Ensure worlds: and groups: exist
        boolean hasWorlds = false, hasGroups = false;
        for (String line : result) {
            String trimmed = line.trim();
            if (trimmed.equals("worlds:") || trimmed.startsWith("worlds: ") || trimmed.startsWith("worlds:#")) hasWorlds = true;
            if (trimmed.equals("groups:") || trimmed.startsWith("groups: ") || trimmed.startsWith("groups:#")) hasGroups = true;
        }
        if (!hasWorlds) {
            result.add("worlds:                        # per-world overrides on top of the above");
        }
        if (!hasGroups) {
            result.add("groups:                        # players with permission ladsapi.group.<name>");
        }

        // Safety net: re-parse result and return original lines unless root.modules is a Map containing every schema module as a top-level key
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(String.join("\n", result));
            if (loaded instanceof Map<?, ?> root) {
                Object modsObj = root.get("modules");
                if (modsObj instanceof Map<?, ?> modsMap) {
                    Set<String> topKeys = new HashSet<>();
                    for (Object k : modsMap.keySet()) {
                        if (k != null) topKeys.add(k.toString().toLowerCase(Locale.ROOT));
                    }
                    boolean allPresent = true;
                    for (LadsSchema.SchemaModule mod : schema.getModules()) {
                        if (!topKeys.contains(mod.getName().toLowerCase(Locale.ROOT))) {
                            allPresent = false;
                            break;
                        }
                    }
                    if (allPresent) {
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            // failed to parse
        }
        return new ArrayList<>(existingLines);
    }

    /** Schema names like "SP: Wood floats" or "Auto GG: Message" break a plain YAML key, so those get double quotes. */
    static String yamlKey(String name) {
        boolean plain = !name.isEmpty() && !name.contains(": ") && !name.contains(" #") && !name.endsWith(":")
                && name.equals(name.trim()) && "-?:,[]{}#&*!|>'\"%@`".indexOf(name.charAt(0)) < 0;
        return plain ? name : "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String formatOptionLine(String indent, String optName, String comment) {
        StringBuilder sb = new StringBuilder();
        String key = yamlKey(optName);
        sb.append(indent).append(key).append(":");
        int pad = Math.max(1, 31 - (indent.length() + key.length() + 1));
        sb.append(" ".repeat(pad));
        sb.append(comment);
        return sb.toString();
    }

    private static int countLeadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static boolean isBlockMappingModule(String line) {
        String trimmed = line.trim();
        int colon = trimmed.indexOf(':');
        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
            int close = trimmed.indexOf(trimmed.charAt(0), 1);
            if (close > 0) {
                colon = trimmed.indexOf(':', close + 1);
            }
        }
        if (colon < 0) return false;
        String rest = trimmed.substring(colon + 1).trim();
        return rest.isEmpty() || rest.startsWith("#");
    }

    private static int findModuleLine(List<String> lines, int start, int end, int modIndentLen, String modName) {
        for (int i = start; i < end; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (countLeadingSpaces(line) == modIndentLen) {
                String key = extractKey(trimmed);
                if (key != null && key.equalsIgnoreCase(modName)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String extractKey(String trimmed) {
        if (trimmed.startsWith("\"")) {
            int close = trimmed.indexOf('"', 1);
            if (close > 1) {
                String after = trimmed.substring(close + 1).trim();
                if (after.startsWith(":")) return trimmed.substring(1, close);
            }
        } else if (trimmed.startsWith("'")) {
            int close = trimmed.indexOf('\'', 1);
            if (close > 1) {
                String after = trimmed.substring(close + 1).trim();
                if (after.startsWith(":")) return trimmed.substring(1, close);
            }
        } else {
            int colon = trimmed.indexOf(':');
            if (colon > 0) {
                return trimmed.substring(0, colon).trim();
            }
        }
        return null;
    }

    private static int findNextModuleOrSection(List<String> lines, int start, int maxEnd, int modIndentLen) {
        for (int i = start; i < maxEnd; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (countLeadingSpaces(line) <= modIndentLen) {
                return i;
            }
        }
        return maxEnd;
    }

    private static Set<String> extractExistingOptionNames(List<String> lines, int start, int end) {
        Set<String> set = new HashSet<>();
        for (int i = start; i < end; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String key = extractKey(trimmed);
            if (key != null) {
                set.add(key.toLowerCase());
            }
        }
        return set;
    }
}
