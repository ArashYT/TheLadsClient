package com.thelads.ladsapi.rules;

import com.thelads.ladsapi.schema.LadsSchema;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

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
        sb.append("# State options: allow | block | on   (on = forced, only for server-controllable modules)\n");
        sb.append("# Option rules: { value: ..., min: ..., max: ..., choices: [...], lock: true/false }\n\n");
        sb.append("modules:\n");

        if (schema != null && schema.getModules() != null) {
            for (LadsSchema.SchemaModule mod : schema.getModules()) {
                sb.append("  ").append(mod.getName()).append(":\n");
                sb.append("    state:                     # allow | block | on   (blank = inherit)\n");
                if (mod.getOptions() != null) {
                    for (LadsSchema.SchemaOption opt : mod.getOptions()) {
                        String optName = opt.getName();
                        sb.append(formatOptionLine(optName, opt.formatComment()));
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
        List<String> updated = updateRulesLines(lines, schema);
        if (!updated.equals(lines)) {
            Files.write(rulesFile, updated, StandardCharsets.UTF_8);
            if (logger != null) {
                logger.info("[LadsClientAPI] rules.yml regenerated with missing schema entries.");
            }
        }
    }

    public static List<String> updateRulesLines(List<String> existingLines, LadsSchema schema) {
        if (schema == null || schema.getModules() == null || schema.getModules().isEmpty()) {
            return new ArrayList<>(existingLines);
        }

        List<String> result = new ArrayList<>(existingLines);

        // Find "modules:" line index
        int modulesIdx = -1;
        for (int i = 0; i < result.size(); i++) {
            String trimmed = result.get(i).trim();
            if (trimmed.equals("modules:") || trimmed.startsWith("modules: #")) {
                modulesIdx = i;
                break;
            }
        }

        if (modulesIdx == -1) {
            // No modules section, append full content
            return List.of(generateRulesContent(schema).split("\n"));
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

        for (LadsSchema.SchemaModule mod : schema.getModules()) {
            String modName = mod.getName();
            int modLineIdx = findModuleLine(result, modulesIdx + 1, modulesEndIdx, modName);

            if (modLineIdx == -1) {
                // Entire module is missing: insert before modulesEndIdx
                List<String> modBlock = new ArrayList<>();
                modBlock.add("  " + modName + ":");
                modBlock.add("    state:                     # allow | block | on   (blank = inherit)");
                if (mod.getOptions() != null) {
                    for (LadsSchema.SchemaOption opt : mod.getOptions()) {
                        modBlock.add(formatOptionLine(opt.getName(), opt.formatComment()));
                    }
                }
                result.addAll(modulesEndIdx, modBlock);
                modulesEndIdx += modBlock.size();
            } else {
                // Module exists, check for missing options
                int nextModOrSection = findNextModuleOrSection(result, modLineIdx + 1, modulesEndIdx);
                Set<String> existingOptions = extractExistingOptionNames(result, modLineIdx + 1, nextModOrSection);

                if (mod.getOptions() != null) {
                    List<String> toInsert = new ArrayList<>();
                    for (LadsSchema.SchemaOption opt : mod.getOptions()) {
                        if (!existingOptions.contains(opt.getName().toLowerCase())) {
                            toInsert.add(formatOptionLine(opt.getName(), opt.formatComment()));
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

        return result;
    }

    private static String formatOptionLine(String optName, String comment) {
        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(optName).append(":");
        int pad = Math.max(1, 31 - (4 + optName.length() + 1));
        sb.append(" ".repeat(pad));
        sb.append(comment);
        return sb.toString();
    }

    private static int findModuleLine(List<String> lines, int start, int end, String modName) {
        String target1 = "  " + modName + ":";
        String target2 = "  \"" + modName + "\":";
        String target3 = "  '" + modName + "':";
        for (int i = start; i < end; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (line.startsWith("  ") && !line.startsWith("    ") && !line.startsWith("   \t")) {
                if (trimmed.equals(target1.trim()) || trimmed.startsWith(target1.trim() + " ") || trimmed.startsWith(target1.trim() + "#")
                    || trimmed.equals(target2.trim()) || trimmed.startsWith(target2.trim() + " ")
                    || trimmed.equals(target3.trim()) || trimmed.startsWith(target3.trim() + " ")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findNextModuleOrSection(List<String> lines, int start, int maxEnd) {
        for (int i = start; i < maxEnd; i++) {
            String line = lines.get(i);
            if (line.isEmpty() || line.trim().startsWith("#")) continue;
            // 2 spaces = next module, 0 spaces = next section
            if (!line.startsWith("    ") && !line.startsWith("   \t")) {
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
            int colon = trimmed.indexOf(':');
            if (colon > 0) {
                String key = trimmed.substring(0, colon).trim();
                if (key.startsWith("\"") && key.endsWith("\"") && key.length() >= 2) {
                    key = key.substring(1, key.length() - 1);
                } else if (key.startsWith("'") && key.endsWith("'") && key.length() >= 2) {
                    key = key.substring(1, key.length() - 1);
                }
                set.add(key.toLowerCase());
            }
        }
        return set;
    }
}
