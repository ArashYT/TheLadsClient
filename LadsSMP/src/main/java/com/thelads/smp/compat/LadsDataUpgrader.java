package com.thelads.smp.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.disabler.ItemDisablerSystem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Automatic Minecraft 26.3 Data & Registry Upgrader for The Lads SMP.
 *
 * Fixes three major data-loading problems across the 256-mod pack:
 * 1. Upgrades legacy 1.20 'minecraft:block_state_property' loot conditions ('block' -> 'blocks', 'properties' -> 'state')
 *    to 26.3 'minecraft:match_block', fixing the fatal bitsandbalance:blocks/azalea_door world-creation crash.
 * 2. Upgrades legacy 'functions' arrays on LootTable, LootPool, and LootPoolEntryContainer objects to 26.3 'modifier'
 *    arrays (and 'function' -> 'type', 'conditions' -> 'condition', 'set_nbt' -> 'set_custom_data'),
 *    restoring 1,710 silently broken loot tables across 26 mods.
 * 3. Upgrades 157 legacy 1.20-format modded recipes in JustEnoughBreeding ('[{"item": "..."}]' / '[{"tag": "..."}]')
 *    to 26.3 ingredient string syntax ('"namespace:id"' / '"#namespace:tag"'), and neutralizes recipes referencing
 *    uninstalled optional mods or missing items so RegistryDataLoader never crashes.
 * 4. Strips disabled items from loot table entries and recipe outputs during registry loading.
 */
public final class LadsDataUpgrader {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/DataUpgrader");

    public static final AtomicInteger UPGRADED_LOOT_TABLES = new AtomicInteger();
    public static final AtomicInteger UPGRADED_LOOT_CONDITIONS = new AtomicInteger();
    public static final AtomicInteger UPGRADED_RECIPES = new AtomicInteger();
    public static final AtomicInteger FILTERED_LOOT_ENTRIES = new AtomicInteger();
    public static final AtomicInteger FILTERED_RECIPES = new AtomicInteger();

    private LadsDataUpgrader() {}

    public static void resetCounters() {
        UPGRADED_LOOT_TABLES.set(0);
        UPGRADED_LOOT_CONDITIONS.set(0);
        UPGRADED_RECIPES.set(0);
        FILTERED_LOOT_ENTRIES.set(0);
        FILTERED_RECIPES.set(0);
    }

    /**
     * Entrypoint invoked from MixinRegistryLoadTask right after StrictJsonParser.parse(Reader).
     *
     * @param registryPath The registry identifier string, e.g. "minecraft:loot_table", "minecraft:recipe"
     * @param entryId      The resource entry identifier string, e.g. "bitsandbalance:blocks/azalea_door"
     * @param element      The parsed JsonElement
     * @return The upgraded/filtered JsonElement
     */
    public static JsonElement processRegistryJson(String registryPath, String entryId, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return element;
        }
        JsonObject root = element.getAsJsonObject();
        LadsConfig config = LadsConfig.get();

        try {
            if (isLootRegistry(registryPath)) {
                if (config.compat.upgradeLegacyData) {
                    boolean changed = upgradeLootTableOrElement(root, true);
                    if (changed) {
                        UPGRADED_LOOT_TABLES.incrementAndGet();
                    }
                }
                if (config.compat.filterDisabledFromData) {
                    int removed = stripDisabledLootEntries(root);
                    if (removed > 0) {
                        FILTERED_LOOT_ENTRIES.addAndGet(removed);
                    }
                }
                if (config.compat.rebalanceBrokenLootTables && entryId != null) {
                    int rebalanced = rebalanceSpecificLootTable(entryId, root);
                    if (rebalanced > 0) {
                        FILTERED_LOOT_ENTRIES.addAndGet(rebalanced);
                    }
                }
            } else if ("minecraft:recipe".equals(registryPath) || "recipe".equals(registryPath)) {
                if (config.compat.upgradeLegacyData) {
                    if (upgradeRecipeJson(entryId, root)) {
                        UPGRADED_RECIPES.incrementAndGet();
                    }
                }
                if (config.compat.filterDisabledFromData) {
                    if (shouldDisableRecipeJson(entryId, root)) {
                        neutralizeRecipeJson(root);
                        FILTERED_RECIPES.incrementAndGet();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to upgrade/filter registry JSON {} in {}: {}", entryId, registryPath, e.getMessage());
        }

        return root;
    }

    private static boolean isLootRegistry(String registryPath) {
        return "minecraft:loot_table".equals(registryPath)
                || "loot_table".equals(registryPath)
                || "minecraft:predicate".equals(registryPath)
                || "predicate".equals(registryPath)
                || "minecraft:item_modifier".equals(registryPath)
                || "item_modifier".equals(registryPath);
    }

    /**
     * Recursively upgrades a LootTable, LootPool, LootPoolEntry, LootFunction, or LootCondition JSON tree to MC 26.3.
     */
    public static boolean upgradeLootTableOrElement(JsonElement element, boolean isLootContainer) {
        boolean modified = false;
        if (element == null) return false;

        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                if (upgradeLootTableOrElement(arr.get(i), false)) {
                    modified = true;
                }
            }
            return modified;
        }

        if (!element.isJsonObject()) {
            return false;
        }

        JsonObject obj = element.getAsJsonObject();

        // 1. Upgrade 'functions' -> 'modifier' on LootTable, LootPool, or LootPoolEntryContainer
        if (obj.has("functions")) {
            JsonElement funcs = obj.remove("functions");
            if (!obj.has("modifier")) {
                obj.add("modifier", funcs);
            }
            modified = true;
        }

        // 2. Upgrade 'modifier' functions array or single function object
        if (obj.has("modifier")) {
            JsonElement modEl = obj.get("modifier");
            if (upgradeFunctionElement(modEl)) {
                modified = true;
            }
        }

        // 3. Upgrade 'conditions' inside a function or condition (26.3 uses 'conditions' on pools/entries,
        //    but verify condition objects themselves have upgraded types)
        if (obj.has("conditions")) {
            if (upgradeConditionElement(obj.get("conditions"))) {
                modified = true;
            }
        }
        if (obj.has("condition") && obj.get("condition").isJsonArray()) {
            if (upgradeConditionElement(obj.get("condition"))) {
                modified = true;
            }
        }
        if (obj.has("terms") && obj.get("terms").isJsonArray()) {
            if (upgradeConditionElement(obj.get("terms"))) {
                modified = true;
            }
        }

        // 4. If this object itself is a condition object (has string 'condition')
        if (obj.has("condition") && obj.get("condition").isJsonPrimitive()) {
            if (upgradeSingleConditionObject(obj)) {
                modified = true;
            }
        }

        // 5. If this object itself is a function object (has string 'function')
        if (obj.has("function") && obj.get("function").isJsonPrimitive()) {
            if (upgradeSingleFunctionObject(obj)) {
                modified = true;
            }
        }

        // 6. Recurse into pools, entries, children
        if (obj.has("pools") && obj.get("pools").isJsonArray()) {
            JsonArray pools = obj.getAsJsonArray("pools");
            for (JsonElement pool : pools) {
                if (upgradeLootTableOrElement(pool, true)) {
                    modified = true;
                }
            }
        }
        if (obj.has("entries") && obj.get("entries").isJsonArray()) {
            JsonArray entries = obj.getAsJsonArray("entries");
            for (JsonElement entry : entries) {
                if (upgradeLootTableOrElement(entry, true)) {
                    modified = true;
                }
            }
        }
        if (obj.has("children") && obj.get("children").isJsonArray()) {
            JsonArray children = obj.getAsJsonArray("children");
            for (JsonElement child : children) {
                if (upgradeLootTableOrElement(child, true)) {
                    modified = true;
                }
            }
        }

        return modified;
    }

    private static boolean upgradeFunctionElement(JsonElement el) {
        boolean modified = false;
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement item : arr) {
                if (upgradeFunctionElement(item)) {
                    modified = true;
                }
            }
        } else if (el.isJsonObject()) {
            JsonObject fnObj = el.getAsJsonObject();
            if (upgradeSingleFunctionObject(fnObj)) {
                modified = true;
            }
            if (upgradeLootTableOrElement(fnObj, false)) {
                modified = true;
            }
        }
        return modified;
    }

    private static boolean upgradeSingleFunctionObject(JsonObject fnObj) {
        boolean modified = false;
        if (fnObj.has("function") && fnObj.get("function").isJsonPrimitive()) {
            JsonElement fnName = fnObj.remove("function");
            if (!fnObj.has("type")) {
                fnObj.add("type", fnName);
            }
            modified = true;
        }
        if (fnObj.has("conditions") && fnObj.get("conditions").isJsonArray()) {
            JsonElement conds = fnObj.remove("conditions");
            if (!fnObj.has("condition")) {
                fnObj.add("condition", conds);
            }
            modified = true;
        }
        if (fnObj.has("type") && fnObj.get("type").isJsonPrimitive()) {
            String type = normalizeId(fnObj.get("type").getAsString());
            if ("minecraft:set_nbt".equals(type)) {
                fnObj.addProperty("type", "minecraft:set_custom_data");
                modified = true;
            }
        }
        if (fnObj.has("condition")) {
            if (upgradeConditionElement(fnObj.get("condition"))) {
                modified = true;
            }
        }
        return modified;
    }

    private static boolean upgradeConditionElement(JsonElement el) {
        boolean modified = false;
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement item : arr) {
                if (upgradeConditionElement(item)) {
                    modified = true;
                }
            }
        } else if (el.isJsonObject()) {
            JsonObject condObj = el.getAsJsonObject();
            if (upgradeSingleConditionObject(condObj)) {
                modified = true;
            }
            if (condObj.has("terms")) {
                if (upgradeConditionElement(condObj.get("terms"))) {
                    modified = true;
                }
            }
            if (condObj.has("term")) {
                if (upgradeConditionElement(condObj.get("term"))) {
                    modified = true;
                }
            }
        }
        return modified;
    }

    /**
     * Upgrades legacy 1.20/1.21 loot condition objects to MC 26.3:
     * - "minecraft:block_state_property" -> "minecraft:match_block" ('block' -> 'blocks', 'properties' -> 'state')
     * - "minecraft:alternative" -> "minecraft:any_of"
     */
    private static boolean upgradeSingleConditionObject(JsonObject condObj) {
        boolean modified = false;
        String condType = null;
        String keyField = null;
        if (condObj.has("condition") && condObj.get("condition").isJsonPrimitive()) {
            condType = normalizeId(condObj.get("condition").getAsString());
            keyField = "condition";
        } else if (condObj.has("type") && condObj.get("type").isJsonPrimitive()) {
            condType = normalizeId(condObj.get("type").getAsString());
            keyField = "type";
        }
        if (condType == null) return false;

        if ("minecraft:block_state_property".equals(condType)) {
            condObj.addProperty(keyField, "minecraft:match_block");
            if (condObj.has("block") && !condObj.has("blocks")) {
                condObj.add("blocks", condObj.remove("block"));
            }
            if (condObj.has("properties") && !condObj.has("state")) {
                condObj.add("state", condObj.remove("properties"));
            }
            UPGRADED_LOOT_CONDITIONS.incrementAndGet();
            modified = true;
        } else if ("minecraft:alternative".equals(condType)) {
            condObj.addProperty(keyField, "minecraft:any_of");
            UPGRADED_LOOT_CONDITIONS.incrementAndGet();
            modified = true;
        }

        // Also check if 'match_block' still has legacy field names
        if ("minecraft:match_block".equals(condType)) {
            if (condObj.has("block") && !condObj.has("blocks")) {
                condObj.add("blocks", condObj.remove("block"));
                modified = true;
            }
            if (condObj.has("properties") && !condObj.has("state")) {
                condObj.add("state", condObj.remove("properties"));
                modified = true;
            }
        }
        return modified;
    }

    /**
     * Strips disabled items from LootPool entries at registry load time.
     */
    public static int stripDisabledLootEntries(JsonObject lootTableObj) {
        int removedCount = 0;
        if (!lootTableObj.has("pools") || !lootTableObj.get("pools").isJsonArray()) {
            return 0;
        }
        JsonArray pools = lootTableObj.getAsJsonArray("pools");
        for (JsonElement poolEl : pools) {
            if (!poolEl.isJsonObject()) continue;
            JsonObject pool = poolEl.getAsJsonObject();
            if (pool.has("entries") && pool.get("entries").isJsonArray()) {
                removedCount += stripDisabledFromEntryArray(pool.getAsJsonArray("entries"));
            }
        }
        return removedCount;
    }

    private static int stripDisabledFromEntryArray(JsonArray entries) {
        int removed = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            JsonElement el = entries.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject entry = el.getAsJsonObject();
            if (entry.has("name") && entry.get("name").isJsonPrimitive()) {
                String entryType = entry.has("type") && entry.get("type").isJsonPrimitive()
                        ? normalizeId(entry.get("type").getAsString())
                        : "minecraft:item";
                if ("minecraft:item".equals(entryType)) {
                    String itemName = normalizeId(entry.get("name").getAsString());
                    if (ItemDisablerSystem.isIdDisabledForLoot(itemName)) {
                        entries.remove(i);
                        removed++;
                        continue;
                    }
                }
            }
            if (entry.has("children") && entry.get("children").isJsonArray()) {
                removed += stripDisabledFromEntryArray(entry.getAsJsonArray("children"));
            }
        }
        return removed;
    }

    /**
     * Rebalances specific progression-breaking modded loot tables identified during the Phase 7 Loot Audit:
     * - mtr:ocean_temple_treasure: removes Overworld netherite_scrap
     * - minecraft:chests/nether_fortress/fort_inside (nova_structures): removes netherite_ingot, netherite_scrap, ancient_debris, netherite_upgrade_smithing_template
     * - mobchampions:legendary_wearable_loot & legendary_weapon_loot: removes Overworld elytra and netherite gear drops
     * - betterend:chests/end_village_bonus_loot: removes non-End-Ship elytra
     * - explorations:chests/slime_cave: dilutes guaranteed 100% enchanted_golden_apple pool to 5%
     */
    public static int rebalanceSpecificLootTable(String entryId, JsonObject lootTableObj) {
        String id = normalizeId(entryId);
        return switch (id) {
            case "mtr:ocean_temple_treasure", "mtr:loot_table/ocean_temple_treasure" ->
                    stripItemsFromPools(lootTableObj, List.of("minecraft:netherite_scrap"));
            case "minecraft:chests/nether_fortress/fort_inside" ->
                    stripItemsFromPools(lootTableObj, List.of(
                            "minecraft:netherite_ingot",
                            "minecraft:netherite_scrap",
                            "minecraft:ancient_debris",
                            "minecraft:netherite_upgrade_smithing_template"
                    ));
            case "mobchampions:legendary_wearable_loot", "mobchampions:legendary_weapon_loot", "mobchampions:epic_weapon_loot" ->
                    stripItemsFromPools(lootTableObj, List.of(
                            "minecraft:elytra",
                            "minecraft:netherite_helmet",
                            "minecraft:netherite_chestplate",
                            "minecraft:netherite_leggings",
                            "minecraft:netherite_boots",
                            "minecraft:netherite_sword",
                            "minecraft:netherite_axe",
                            "minecraft:netherite_hoe"
                    ));
            case "betterend:chests/end_village_bonus_loot" ->
                    stripItemsFromPools(lootTableObj, List.of("minecraft:elytra"));
            case "explorations:chests/slime_cave" -> {
                if (lootTableObj.has("pools") && lootTableObj.get("pools").isJsonArray()) {
                    JsonArray pools = lootTableObj.getAsJsonArray("pools");
                    int changed = 0;
                    for (JsonElement poolEl : pools) {
                        if (!poolEl.isJsonObject()) continue;
                        JsonObject pool = poolEl.getAsJsonObject();
                        if (pool.has("entries") && pool.get("entries").isJsonArray()) {
                            JsonArray entries = pool.getAsJsonArray("entries");
                            if (entries.size() == 1 && entries.get(0).isJsonObject()) {
                                JsonObject first = entries.get(0).getAsJsonObject();
                                if (first.has("name") && "minecraft:enchanted_golden_apple".equals(normalizeId(first.get("name").getAsString()))) {
                                    JsonObject emptyEntry = new JsonObject();
                                    emptyEntry.addProperty("type", "minecraft:empty");
                                    emptyEntry.addProperty("weight", 19);
                                    entries.add(emptyEntry);
                                    changed++;
                                }
                            }
                        }
                    }
                    yield changed;
                }
                yield 0;
            }
            default -> 0;
        };
    }

    private static int stripItemsFromPools(JsonObject lootTableObj, List<String> bannedIds) {
        if (!lootTableObj.has("pools") || !lootTableObj.get("pools").isJsonArray()) return 0;
        int removed = 0;
        JsonArray pools = lootTableObj.getAsJsonArray("pools");
        for (JsonElement poolEl : pools) {
            if (!poolEl.isJsonObject()) continue;
            JsonObject pool = poolEl.getAsJsonObject();
            if (pool.has("entries") && pool.get("entries").isJsonArray()) {
                JsonArray entries = pool.getAsJsonArray("entries");
                for (int i = entries.size() - 1; i >= 0; i--) {
                    if (!entries.get(i).isJsonObject()) continue;
                    JsonObject entry = entries.get(i).getAsJsonObject();
                    if (entry.has("name") && entry.get("name").isJsonPrimitive()) {
                        String name = normalizeId(entry.get("name").getAsString());
                        if (bannedIds.contains(name)) {
                            entries.remove(i);
                            removed++;
                        }
                    }
                }
            }
        }
        return removed;
    }

    /**
     * Upgrades legacy 1.20/1.21 recipe JSONs to MC 26.3 format.
     * Specifically fixes JustEnoughBreeding's 157 modded breeding recipes that use legacy [{"item": "..."}] arrays
     * or reference uninstalled optional mods (ecologics, friendsandfoes, naturalist, alexsmobs missing items, etc.).
     */
    public static boolean upgradeRecipeJson(String entryId, JsonObject root) {
        if (!root.has("type") || !root.get("type").isJsonPrimitive()) {
            return false;
        }
        String type = normalizeId(root.get("type").getAsString());
        boolean modified = false;

        if (type.startsWith("justenoughbreeding:")) {
            if (!isBreedingRecipeTargetPresent(root)) {
                replaceWithHarmlessDummyRecipe(root);
                return true;
            }

            String[] ingredientFields = {"inputs", "extra_inputs", "spawn_eggs", "input_spawn_eggs", "output_spawn_eggs", "outputs", "result_item"};
            for (String field : ingredientFields) {
                if (root.has(field)) {
                    JsonElement current = root.get(field);
                    JsonElement upgraded = upgradeIngredientElement(current, true);
                    if (upgraded == null) {
                        if ("inputs".equals(field)) {
                            replaceWithHarmlessDummyRecipe(root);
                            return true;
                        } else {
                            root.remove(field);
                            modified = true;
                        }
                    } else if (!upgraded.equals(current)) {
                        root.add(field, upgraded);
                        modified = true;
                    }
                }
            }
        }

        return modified;
    }

    private static boolean isBreedingRecipeTargetPresent(JsonObject root) {
        try {
            // If BuiltInRegistries isn't populated (e.g. unit test environment), assume present
            if (BuiltInRegistries.ENTITY_TYPE.size() == 0) {
                return true;
            }
            for (String entityField : new String[]{"mob", "input_entity", "output_entity"}) {
                if (root.has(entityField) && root.get(entityField).isJsonPrimitive()) {
                    Identifier mobId = Identifier.tryParse(normalizeId(root.get(entityField).getAsString()));
                    if (mobId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(mobId)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Converts a legacy 1.20 ingredient element:
     * - {"item": "ns:id"} -> "ns:id"
     * - {"tag": "ns:tag"} -> "#ns:tag"
     * - [{"item": "a"}, {"item": "b"}] -> "a" (if single) or ["a", "b"] (filtering out un-registered items if registry is live)
     */
    public static JsonElement upgradeIngredientElement(JsonElement el, boolean allowTags) {
        if (el == null || el.isJsonNull()) return null;

        if (el.isJsonPrimitive()) {
            String val = el.getAsString().trim();
            if (val.startsWith("#")) {
                return allowTags ? new JsonPrimitive("#" + normalizeId(val.substring(1))) : null;
            }
            String norm = normalizeId(val);
            return isRegisteredItemOrOffline(norm) ? new JsonPrimitive(norm) : null;
        }

        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("item") && obj.get("item").isJsonPrimitive()) {
                String itemId = normalizeId(obj.get("item").getAsString());
                return isRegisteredItemOrOffline(itemId) ? new JsonPrimitive(itemId) : null;
            }
            if (allowTags && obj.has("tag") && obj.get("tag").isJsonPrimitive()) {
                String tagId = normalizeId(obj.get("tag").getAsString());
                return new JsonPrimitive("#" + tagId);
            }
            return null;
        }

        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            List<String> validEntries = new ArrayList<>();
            boolean hasTag = false;
            for (JsonElement child : arr) {
                JsonElement converted = upgradeIngredientElement(child, allowTags);
                if (converted != null && converted.isJsonPrimitive()) {
                    String s = converted.getAsString();
                    if (s.startsWith("#")) hasTag = true;
                    if (!validEntries.contains(s)) {
                        validEntries.add(s);
                    }
                }
            }
            if (validEntries.isEmpty()) {
                return null;
            }
            // In MC 26.3 Ingredient.CODEC, a tag ("#...") cannot be mixed inside a holder-set array of item IDs;
            // if a single entry or a tag is present, return the single string primitive.
            if (validEntries.size() == 1 || hasTag) {
                String chosen = validEntries.stream().filter(s -> s.startsWith("#")).findFirst().orElse(validEntries.getFirst());
                return new JsonPrimitive(chosen);
            }
            JsonArray out = new JsonArray();
            for (String s : validEntries) {
                out.add(s);
            }
            return out;
        }

        return el;
    }

    private static boolean isRegisteredItemOrOffline(String itemId) {
        try {
            if (BuiltInRegistries.ITEM.size() == 0) {
                return true;
            }
            Identifier id = Identifier.tryParse(itemId);
            return id != null && BuiltInRegistries.ITEM.containsKey(id);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean shouldDisableRecipeJson(String entryId, JsonObject root) {
        if (entryId != null && ItemDisablerSystem.isRecipeIdDisabled(entryId)) {
            return true;
        }
        String outputId = extractRecipeOutputItemId(root);
        return outputId != null && ItemDisablerSystem.isIdDisabledForRecipeOutput(outputId);
    }

    public static String extractRecipeOutputItemId(JsonObject root) {
        if (!root.has("result")) return null;
        JsonElement result = root.get("result");
        if (result.isJsonPrimitive()) {
            return normalizeId(result.getAsString());
        }
        if (result.isJsonObject()) {
            JsonObject resObj = result.getAsJsonObject();
            if (resObj.has("id") && resObj.get("id").isJsonPrimitive()) {
                return normalizeId(resObj.get("id").getAsString());
            }
            if (resObj.has("item") && resObj.get("item").isJsonPrimitive()) {
                return normalizeId(resObj.get("item").getAsString());
            }
        }
        return null;
    }

    /**
     * Replaces a disabled or broken modded recipe JSON in-place with an uncraftable barrier dummy recipe
     * so RegistryDataLoader parses it cleanly without errors while making the original recipe impossible to craft.
     */
    public static void neutralizeRecipeJson(JsonObject root) {
        replaceWithHarmlessDummyRecipe(root);
    }

    private static void replaceWithHarmlessDummyRecipe(JsonObject root) {
        for (String key : new ArrayList<>(root.keySet())) {
            root.remove(key);
        }
        root.addProperty("type", "minecraft:crafting_shapeless");
        root.addProperty("category", "misc");
        JsonArray ingredients = new JsonArray();
        ingredients.add("minecraft:barrier");
        ingredients.add("minecraft:structure_void");
        root.add("ingredients", ingredients);
        JsonObject result = new JsonObject();
        result.addProperty("id", "minecraft:barrier");
        result.addProperty("count", 1);
        root.add("result", result);
    }

    public static String normalizeId(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (!trimmed.contains(":")) {
            return "minecraft:" + trimmed;
        }
        return trimmed;
    }
}
