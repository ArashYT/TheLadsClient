package com.thelads.ladsapi.policy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thelads.ladsapi.config.LadsConfig;
import com.thelads.ladsapi.platform.LadsPlayerHandle;
import com.thelads.ladsapi.rules.LadsRules;
import com.thelads.ladsapi.rules.ModuleRule;
import com.thelads.ladsapi.rules.RuleOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds full policy snapshot JSON per PROTOCOL.md.
 */
public final class PolicyBuilder {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private PolicyBuilder() {}

    public static String buildPolicyJson(
            LadsRules rules,
            LadsConfig config,
            LadsPlayerHandle player,
            Map<String, ModuleRule> apiOverrides,
            int rev
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("t", "policy");
        root.addProperty("rev", rev);

        // ladsapi.bypass = empty policy
        if (player != null && player.hasPermission("ladsapi.bypass")) {
            root.addProperty("unlisted", "inherit");
            root.add("modules", new JsonObject());
            root.add("options", new JsonObject());
            return GSON.toJson(root);
        }

        String unlisted = (config != null && config.getUnlistedModules() != null)
                ? config.getUnlistedModules()
                : "inherit";
        root.addProperty("unlisted", unlisted);

        // Merge order: rules.yml base -> worlds.<world> -> groups.<g> -> API overrides
        Map<String, ModuleRule> merged = new LinkedHashMap<>();

        if (rules != null && rules.getModules() != null) {
            for (Map.Entry<String, ModuleRule> e : rules.getModules().entrySet()) {
                merged.put(e.getKey(), ModuleRule.copyOf(e.getValue()));
            }

            if (player != null && player.world() != null && rules.getWorlds() != null) {
                Map<String, ModuleRule> wMods = rules.getWorlds().get(player.world());
                if (wMods != null) {
                    for (Map.Entry<String, ModuleRule> e : wMods.entrySet()) {
                        merged.computeIfAbsent(e.getKey(), k -> new ModuleRule()).mergeFrom(e.getValue());
                    }
                }
            }

            if (player != null && rules.getGroups() != null) {
                for (Map.Entry<String, Map<String, ModuleRule>> gEntry : rules.getGroups().entrySet()) {
                    String groupName = gEntry.getKey();
                    if (player.hasPermission("ladsapi.group." + groupName)) {
                        for (Map.Entry<String, ModuleRule> e : gEntry.getValue().entrySet()) {
                            merged.computeIfAbsent(e.getKey(), k -> new ModuleRule()).mergeFrom(e.getValue());
                        }
                    }
                }
            }
        }

        if (apiOverrides != null) {
            for (Map.Entry<String, ModuleRule> e : apiOverrides.entrySet()) {
                merged.computeIfAbsent(e.getKey(), k -> new ModuleRule()).mergeFrom(e.getValue());
            }
        }

        // Build modules and options objects
        JsonObject modulesObj = new JsonObject();
        JsonObject optionsObj = new JsonObject();

        for (Map.Entry<String, ModuleRule> entry : merged.entrySet()) {
            String modName = entry.getKey();
            ModuleRule modRule = entry.getValue();

            if (modRule.getState() != null && !modRule.getState().isBlank()) {
                modulesObj.addProperty(modName, modRule.getState().toLowerCase());
            }

            if (!modRule.getOptions().isEmpty()) {
                JsonObject modOptions = new JsonObject();
                for (Map.Entry<String, RuleOption> optEntry : modRule.getOptions().entrySet()) {
                    String optName = optEntry.getKey();
                    RuleOption optRule = optEntry.getValue();
                    if (optRule != null && !optRule.isEmpty()) {
                        JsonObject optObj = new JsonObject();
                        if (optRule.getValue() != null) {
                            optObj.add("value", toJsonPrimitive(optRule.getValue()));
                        }
                        if (optRule.getMin() != null) {
                            optObj.add("min", formatNumPrimitive(optRule.getMin()));
                        }
                        if (optRule.getMax() != null) {
                            optObj.add("max", formatNumPrimitive(optRule.getMax()));
                        }
                        if (optRule.getChoices() != null && !optRule.getChoices().isEmpty()) {
                            JsonArray arr = new JsonArray();
                            for (String c : optRule.getChoices()) arr.add(c);
                            optObj.add("choices", arr);
                        }
                        if (Boolean.TRUE.equals(optRule.getLock())) {
                            optObj.addProperty("lock", true);
                        }
                        if (!optObj.keySet().isEmpty()) {
                            modOptions.add(optName, optObj);
                        }
                    }
                }
                if (!modOptions.keySet().isEmpty()) {
                    optionsObj.add(modName, modOptions);
                }
            }
        }

        root.add("modules", modulesObj);
        root.add("options", optionsObj);

        if (config != null && config.getNotice() != null && !config.getNotice().isBlank()) {
            root.addProperty("notice", config.getNotice());
        }

        return GSON.toJson(root);
    }

    private static JsonPrimitive toJsonPrimitive(Object val) {
        if (val instanceof Boolean b) return new JsonPrimitive(b);
        if (val instanceof Number n) return formatNumPrimitive(n.doubleValue());
        return new JsonPrimitive(String.valueOf(val));
    }

    private static JsonPrimitive formatNumPrimitive(double d) {
        if (d == (long) d && !Double.isInfinite(d) && !Double.isNaN(d)) {
            return new JsonPrimitive((long) d);
        }
        return new JsonPrimitive(d);
    }
}
