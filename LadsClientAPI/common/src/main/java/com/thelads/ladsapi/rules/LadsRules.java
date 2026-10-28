package com.thelads.ladsapi.rules;

import com.thelads.ladsapi.schema.LadsSchema;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Parsed representation of rules.yml.
 */
public final class LadsRules {
    private final Map<String, ModuleRule> modules = new LinkedHashMap<>();
    private final Map<String, Map<String, ModuleRule>> worlds = new LinkedHashMap<>();
    private final Map<String, Map<String, ModuleRule>> groups = new LinkedHashMap<>();

    public static LadsRules load(Path dataFolder, LadsSchema schema, Logger logger) {
        LadsRules rules = new LadsRules();
        Path rulesFile = dataFolder.resolve("rules.yml");
        try {
            RulesGenerator.regenerate(rulesFile, schema, logger);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("[LadsClientAPI] Error regenerating rules.yml: " + e.getMessage());
            }
        }
        try {
            rules.readFrom(rulesFile, logger);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("[LadsClientAPI] Error loading rules.yml: " + e.getMessage());
            }
        }
        return rules;
    }

    public void readFrom(Path rulesFile, Logger logger) {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(rulesFile)) {
            Map<String, Object> data = yaml.load(in);
            if (data != null) {
                parseYaml(data, logger);
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("[LadsClientAPI] Error parsing rules.yml: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void parseYaml(Map<String, Object> data) {
        parseYaml(data, null);
    }

    @SuppressWarnings("unchecked")
    public void parseYaml(Map<String, Object> data, Logger logger) {
        modules.clear();
        worlds.clear();
        groups.clear();

        if (data == null) return;

        Object modsObj = data.get("modules");
        if (modsObj instanceof Map<?, ?> modsMap) {
            parseModulesMap((Map<String, Object>) modsMap, this.modules, logger);
        }

        Object worldsObj = data.get("worlds");
        if (worldsObj instanceof Map<?, ?> worldsMap) {
            for (Map.Entry<?, ?> entry : worldsMap.entrySet()) {
                String worldName = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> wContent) {
                    Map<String, ModuleRule> wMods = new LinkedHashMap<>();
                    Object subMods = wContent.get("modules");
                    if (subMods instanceof Map<?, ?> subModsMap) {
                        parseModulesMap((Map<String, Object>) subModsMap, wMods, logger);
                    } else {
                        parseModulesMap((Map<String, Object>) wContent, wMods, logger);
                    }
                    this.worlds.put(worldName, wMods);
                }
            }
        }

        Object groupsObj = data.get("groups");
        if (groupsObj instanceof Map<?, ?> groupsMap) {
            for (Map.Entry<?, ?> entry : groupsMap.entrySet()) {
                String groupName = String.valueOf(entry.getKey());
                if (entry.getValue() instanceof Map<?, ?> gContent) {
                    Map<String, ModuleRule> gMods = new LinkedHashMap<>();
                    Object subMods = gContent.get("modules");
                    if (subMods instanceof Map<?, ?> subModsMap) {
                        parseModulesMap((Map<String, Object>) subModsMap, gMods, logger);
                    } else {
                        parseModulesMap((Map<String, Object>) gContent, gMods, logger);
                    }
                    this.groups.put(groupName, gMods);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void parseModulesMap(Map<String, Object> map, Map<String, ModuleRule> target, Logger logger) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String modName = entry.getKey();
            Object val = entry.getValue();
            ModuleRule modRule = new ModuleRule();

            if (val instanceof Map<?, ?> modData) {
                for (Map.Entry<?, ?> optEntry : modData.entrySet()) {
                    String optKey = String.valueOf(optEntry.getKey());
                    Object optVal = optEntry.getValue();

                    if ("state".equalsIgnoreCase(optKey)) {
                        if (optVal != null) {
                            String s;
                            if (optVal instanceof Boolean b) {
                                s = b ? "on" : "inherit";
                            } else {
                                s = String.valueOf(optVal).trim().toLowerCase();
                                if ("true".equals(s)) s = "on";
                                else if ("false".equals(s)) s = "inherit";
                            }
                            if (!s.isEmpty()) {
                                if (!s.equals("allow") && !s.equals("block") && !s.equals("on") && !s.equals("inherit")) {
                                    if (logger != null) {
                                        logger.warning("[LadsClientAPI] Unknown module state '" + s + "' for module " + modName + " (expected: allow | block | on)");
                                    }
                                }
                                modRule.setState(s);
                            }
                        }
                    } else {
                        RuleOption optRule = parseOptionRule(optVal);
                        if (optRule != null && !optRule.isEmpty()) {
                            modRule.setOption(optKey, optRule);
                        }
                    }
                }
            } else if (val instanceof String strVal) {
                String s = strVal.trim().toLowerCase();
                if ("true".equals(s)) s = "on";
                else if ("false".equals(s)) s = "inherit";
                if (!s.isEmpty()) {
                    if (!s.equals("allow") && !s.equals("block") && !s.equals("on") && !s.equals("inherit")) {
                        if (logger != null) {
                            logger.warning("[LadsClientAPI] Unknown module state '" + s + "' for module " + modName + " (expected: allow | block | on)");
                        }
                    }
                    modRule.setState(s);
                }
            } else if (val instanceof Boolean b) {
                modRule.setState(b ? "on" : "inherit");
            }
            target.put(modName, modRule);
        }
    }

    @SuppressWarnings("unchecked")
    private RuleOption parseOptionRule(Object val) {
        if (val == null) return null;
        RuleOption opt = new RuleOption();
        if (val instanceof Map<?, ?> map) {
            if (map.containsKey("value")) {
                opt.setValue(map.get("value"));
            }
            if (map.containsKey("min")) {
                opt.setMin(asDouble(map.get("min")));
            }
            if (map.containsKey("max")) {
                opt.setMax(asDouble(map.get("max")));
            }
            if (map.containsKey("choices")) {
                Object c = map.get("choices");
                if (c instanceof List<?> list) {
                    List<String> strList = new ArrayList<>();
                    for (Object item : list) strList.add(String.valueOf(item));
                    opt.setChoices(strList);
                }
            }
            if (map.containsKey("lock")) {
                opt.setLock(Boolean.parseBoolean(String.valueOf(map.get("lock"))));
            }
        } else if (val instanceof Boolean b) {
            opt.setValue(b);
        } else if (val instanceof Number n) {
            opt.setValue(n);
        } else if (val instanceof String s) {
            opt.setValue(s);
        }
        return opt;
    }

    private static Double asDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        if (obj != null) {
            try {
                return Double.parseDouble(String.valueOf(obj));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public Map<String, ModuleRule> getModules() { return modules; }
    public Map<String, Map<String, ModuleRule>> getWorlds() { return worlds; }
    public Map<String, Map<String, ModuleRule>> getGroups() { return groups; }
}
