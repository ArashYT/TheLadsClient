package com.thelads.smp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.smp.compat.LadsDataUpgrader;
import com.thelads.smp.config.LadsConfig;
import com.thelads.smp.disabler.DisabledContentConfig;
import com.thelads.smp.disabler.ItemDisablerSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated JUnit 5 verification tests for The Lads SMP:
 * - Item & Content Disabling rule engine (exact IDs, globs, loot-only, recipe-only, protected blocks)
 * - 26.3 Data Upgrader (bitsandbalance azalea_door fix, 1,710 loot table functions->modifier, JustEnoughBreeding recipes)
 * - Loot progression rebalancing (MTR Ocean Temple, Nether Fortress, MobChampions, Explorations Slime Cave)
 * - Configuration persistence round-trip
 */
public class LadsSMPUnitTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        LadsConfig.setInstanceForTesting(new LadsConfig());
        ItemDisablerSystem.applyConfig(new DisabledContentConfig());
        LadsDataUpgrader.resetCounters();
    }

    @Test
    void testGlobalAndWildcardDisablerRules() {
        assertTrue(ItemDisablerSystem.isIdDisabled("smartbackpacks:flight_upgrade"));
        assertTrue(ItemDisablerSystem.isIdDisabled("smartbackpacks:repair_upgrade"));
        assertTrue(ItemDisablerSystem.isIdDisabled("usefulhats:halo"));
        assertTrue(ItemDisablerSystem.isIdDisabled("femboymod:astolfo_backpack"));
        assertTrue(ItemDisablerSystem.isIdDisabled("femboymod:netherite_backpack"));
        assertFalse(ItemDisablerSystem.isIdDisabled("minecraft:diamond_pickaxe"));
        assertFalse(ItemDisablerSystem.isIdDisabled("smartbackpacks:iron_backpack"));
    }

    @Test
    void testLootOnlyAndRecipeOnlyDisablerRules() {
        // Nether Star and Dragon Egg are blocked in loot tables, but not globally banned from boss drops/inventories
        assertFalse(ItemDisablerSystem.isIdDisabled("minecraft:nether_star"));
        assertTrue(ItemDisablerSystem.isIdDisabledForLoot("minecraft:nether_star"));
        assertTrue(ItemDisablerSystem.isIdDisabledForLoot("minecraft:dragon_egg"));

        // Mimicream is blocked in recipe outputs/inputs to prevent item duplication, but allowed as a mob drop
        assertFalse(ItemDisablerSystem.isIdDisabled("alexsmobs:mimicream"));
        assertTrue(ItemDisablerSystem.isIdDisabledForRecipeOutput("alexsmobs:mimicream"));
        assertTrue(ItemDisablerSystem.isRecipeIdDisabled("alexsmobs:mimicream_repair"));
    }

    @Test
    void testProtectedBlockCarryOnRules() {
        assertTrue(ItemDisablerSystem.isProtectedBlockId("minecraft:spawner"));
        assertTrue(ItemDisablerSystem.isProtectedBlockId("minecraft:trial_spawner"));
        assertTrue(ItemDisablerSystem.isProtectedBlockId("minecraft:vault"));
        assertTrue(ItemDisablerSystem.isProtectedBlockId("lootr:lootr_chest"));
        assertTrue(ItemDisablerSystem.isProtectedBlockId("waystones:waystone"));
        assertFalse(ItemDisablerSystem.isProtectedBlockId("minecraft:chest"));
    }

    @Test
    void testUpgradeBitsAndBalanceAzaleaDoorLootTable() {
        String azaleaDoorJson = """
                {
                  "type": "minecraft:block",
                  "pools": [
                    {
                      "rolls": 1.0,
                      "entries": [
                        {
                          "type": "minecraft:item",
                          "name": "bitsandbalance:azalea_door",
                          "conditions": [
                            {
                              "block": "bitsandbalance:azalea_door",
                              "condition": "minecraft:block_state_property",
                              "properties": {
                                "half": "lower"
                              }
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
        JsonElement parsed = JsonParser.parseString(azaleaDoorJson);
        JsonObject upgraded = LadsDataUpgrader.processRegistryJson(
                "minecraft:loot_table", "bitsandbalance:blocks/azalea_door", parsed).getAsJsonObject();

        JsonObject cond = upgraded.getAsJsonArray("pools")
                .get(0).getAsJsonObject()
                .getAsJsonArray("entries")
                .get(0).getAsJsonObject()
                .getAsJsonArray("conditions")
                .get(0).getAsJsonObject();

        assertEquals("minecraft:match_block", cond.get("condition").getAsString());
        assertEquals("bitsandbalance:azalea_door", cond.get("blocks").getAsString());
        assertEquals("lower", cond.getAsJsonObject("state").get("half").getAsString());
        assertFalse(cond.has("block"));
        assertFalse(cond.has("properties"));
        assertTrue(LadsDataUpgrader.UPGRADED_LOOT_CONDITIONS.get() >= 1);
    }

    @Test
    void testUpgradeLegacyLootFunctionsTo26_3Modifier() {
        String legacySlabLoot = """
                {
                  "type": "minecraft:block",
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {
                          "type": "minecraft:item",
                          "name": "mcwpaths:and_paving_slab",
                          "functions": [
                            {
                              "function": "minecraft:set_count",
                              "count": 2,
                              "conditions": [
                                {
                                  "condition": "minecraft:block_state_property",
                                  "block": "mcwpaths:and_paving_slab",
                                  "properties": {
                                    "type": "double"
                                  }
                                }
                              ]
                            },
                            {
                              "function": "minecraft:explosion_decay"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
        JsonObject upgraded = LadsDataUpgrader.processRegistryJson(
                "minecraft:loot_table", "mcwpaths:blocks/and_paving_slab", JsonParser.parseString(legacySlabLoot)).getAsJsonObject();

        JsonObject entry = upgraded.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject();

        assertFalse(entry.has("functions"), "Legacy 'functions' key should be renamed to 'modifier'");
        assertTrue(entry.has("modifier"), "26.3 'modifier' key must be present");

        JsonArray modifiers = entry.getAsJsonArray("modifier");
        assertEquals(2, modifiers.size());
        JsonObject setCount = modifiers.get(0).getAsJsonObject();
        assertEquals("minecraft:set_count", setCount.get("type").getAsString());
        assertTrue(setCount.has("condition"));

        JsonObject matchBlock = setCount.getAsJsonArray("condition").get(0).getAsJsonObject();
        assertEquals("minecraft:match_block", matchBlock.get("condition").getAsString());
        assertEquals("mcwpaths:and_paving_slab", matchBlock.get("blocks").getAsString());
        assertEquals("double", matchBlock.getAsJsonObject("state").get("type").getAsString());
    }

    @Test
    void testUpgradeJustEnoughBreedingLegacyRecipe() {
        String legacyGlareTaming = """
                {
                  "type": "justenoughbreeding:taming",
                  "mod": "friendsandfoes",
                  "input_entity": "friendsandfoes:glare",
                  "inputs": [
                    {
                      "item": "minecraft:glow_berries"
                    }
                  ],
                  "spawn_eggs": [
                    {
                      "item": "friendsandfoes:glare_spawn_egg"
                    }
                  ]
                }
                """;
        JsonObject upgraded = LadsDataUpgrader.processRegistryJson(
                "minecraft:recipe", "justenoughbreeding:taming/friendsandfoes/glare", JsonParser.parseString(legacyGlareTaming)).getAsJsonObject();

        assertEquals("minecraft:glow_berries", upgraded.get("inputs").getAsString());
        assertEquals("friendsandfoes:glare_spawn_egg", upgraded.get("spawn_eggs").getAsString());
    }

    @Test
    void testLootTableDisablerAndProgressionRebalancing() {
        // 1. Test stripping Nether Star from MNS library chest
        String mnsLibrary = """
                {
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {"type": "minecraft:item", "name": "minecraft:book", "weight": 10},
                        {"type": "minecraft:item", "name": "minecraft:nether_star", "weight": 1},
                        {"type": "minecraft:item", "name": "smartbackpacks:flight_upgrade", "weight": 1}
                      ]
                    }
                  ]
                }
                """;
        JsonObject upgradedMns = LadsDataUpgrader.processRegistryJson(
                "minecraft:loot_table", "mns:chests/mega_fortress/intact_library", JsonParser.parseString(mnsLibrary)).getAsJsonObject();
        JsonArray entries = upgradedMns.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
        assertEquals(1, entries.size());
        assertEquals("minecraft:book", entries.get(0).getAsJsonObject().get("name").getAsString());

        // 2. Test diluting Explorations Slime Cave 100% Enchanted Golden Apple to 5% (weight 1 vs empty weight 19)
        String slimeCave = """
                {
                  "pools": [
                    {
                      "rolls": 1,
                      "entries": [
                        {"type": "minecraft:item", "name": "minecraft:enchanted_golden_apple", "weight": 1}
                      ]
                    }
                  ]
                }
                """;
        JsonObject upgradedSlime = LadsDataUpgrader.processRegistryJson(
                "minecraft:loot_table", "explorations:chests/slime_cave", JsonParser.parseString(slimeCave)).getAsJsonObject();
        JsonArray slimeEntries = upgradedSlime.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
        assertEquals(2, slimeEntries.size());
        assertEquals("minecraft:empty", slimeEntries.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals(19, slimeEntries.get(1).getAsJsonObject().get("weight").getAsInt());
    }

    @Test
    void testConfigPersistenceRoundTrip() {
        Path cfgPath = tempDir.resolve("config.json");
        LadsConfig cfg = new LadsConfig();
        cfg.features.eggHatchChance = 0.5;
        LadsConfig.save(cfgPath, cfg);

        LadsConfig loaded = LadsConfig.load(cfgPath);
        assertEquals(0.5, loaded.features.eggHatchChance, 1e-6);

        Path disablerPath = tempDir.resolve("disabled_items.json");
        DisabledContentConfig disCfg = new DisabledContentConfig();
        disCfg.disabledItems.add("testmod:op_sword");
        DisabledContentConfig.save(disablerPath, disCfg);

        ItemDisablerSystem.reload(disablerPath);
        assertTrue(ItemDisablerSystem.isIdDisabled("testmod:op_sword"));
    }

    @Test
    void testAllModpackJarsLootTablesAndRecipesUpgradeCleanly() throws Exception {
        Path refModsDir = Path.of("C:\\Users\\Arash\\curseforge\\minecraft\\Instances\\Test For Lads SMP 26.3\\mods");
        if (!java.nio.file.Files.isDirectory(refModsDir)) {
            return;
        }

        int totalJars = 0;
        int totalLootTables = 0;
        int totalRecipes = 0;

        try (var stream = java.nio.file.Files.list(refModsDir)) {
            for (Path jarPath : stream.filter(p -> p.toString().endsWith(".jar")).toList()) {
                totalJars++;
                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarPath.toFile())) {
                    var entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        java.util.zip.ZipEntry ze = entries.nextElement();
                        String name = ze.getName();
                        if (!name.startsWith("data/") || !name.endsWith(".json")) continue;

                        String[] parts = name.split("/", 4);
                        if (parts.length < 4) continue;
                        String namespace = parts[1];
                        String category = parts[2];
                        String relPath = parts[3].substring(0, parts[3].length() - 5);

                        if (category.equals("loot_table") || category.equals("loot_tables")) {
                            totalLootTables++;
                            try (var reader = new java.io.InputStreamReader(zf.getInputStream(ze), java.nio.charset.StandardCharsets.UTF_8)) {
                                JsonElement parsed = JsonParser.parseReader(reader);
                                JsonElement res = LadsDataUpgrader.processRegistryJson(
                                        "minecraft:loot_table", namespace + ":" + relPath, parsed);
                                assertNotNull(res);
                                // Verify no un-upgraded block_state_property remains
                                assertFalse(res.toString().contains("\"minecraft:block_state_property\""),
                                        "Remaining block_state_property in " + namespace + ":" + relPath);
                            }
                        } else if (category.equals("recipe") || category.equals("recipes")) {
                            totalRecipes++;
                            try (var reader = new java.io.InputStreamReader(zf.getInputStream(ze), java.nio.charset.StandardCharsets.UTF_8)) {
                                JsonElement parsed = JsonParser.parseReader(reader);
                                JsonElement res = LadsDataUpgrader.processRegistryJson(
                                        "minecraft:recipe", namespace + ":" + relPath, parsed);
                                assertNotNull(res);
                            }
                        }
                    }
                }
            }
        }

        System.out.printf("[LadsSMP Full-Pack Verification] Jars=%d, LootTables=%d, Recipes=%d, UpgradedTables=%d, UpgradedConditions=%d, UpgradedRecipes=%d, FilteredLootEntries=%d, FilteredRecipes=%d%n",
                totalJars,
                totalLootTables,
                totalRecipes,
                LadsDataUpgrader.UPGRADED_LOOT_TABLES.get(),
                LadsDataUpgrader.UPGRADED_LOOT_CONDITIONS.get(),
                LadsDataUpgrader.UPGRADED_RECIPES.get(),
                LadsDataUpgrader.FILTERED_LOOT_ENTRIES.get(),
                LadsDataUpgrader.FILTERED_RECIPES.get());

        assertEquals(256, totalJars, "All 256 reference mod jars should be scanned");
        assertTrue(LadsDataUpgrader.UPGRADED_LOOT_TABLES.get() >= 1700, "At least 1,700 legacy loot tables should be upgraded");
        assertTrue(LadsDataUpgrader.UPGRADED_LOOT_CONDITIONS.get() >= 75, "At least 75 block_state_property conditions should be upgraded");
        assertEquals(160, LadsDataUpgrader.UPGRADED_RECIPES.get(), "All 160 legacy JustEnoughBreeding recipes should be upgraded");
        assertTrue(LadsDataUpgrader.FILTERED_LOOT_ENTRIES.get() >= 6, "All progression-breaking/disabled loot entries should be filtered");
    }

    @Test
    void testDevInstanceActiveModsDependencyAndDataIntegrity() throws Exception {
        Path devModsDir = Path.of("C:\\Users\\Arash\\AppData\\Roaming\\.theladsclient\\instances\\lads-smp-26.3-dev\\minecraft\\mods");
        if (!java.nio.file.Files.isDirectory(devModsDir)) {
            return;
        }

        java.util.Set<String> providedIds = new java.util.HashSet<>(java.util.List.of(
                "minecraft", "java", "fabricloader", "fabric-api", "fabric"
        ));
        java.util.Map<String, String> primaryModIdToJar = new java.util.HashMap<>();
        java.util.Map<String, java.util.List<String>> modDepends = new java.util.HashMap<>();

        var jars = java.nio.file.Files.list(devModsDir).filter(p -> p.toString().endsWith(".jar")).toList();
        assertEquals(225, jars.size(), "Dev instance should have exactly 225 active mod jars (256 - 32 + 1 ladssmp)");

        for (Path jarPath : jars) {
            try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jarPath.toFile())) {
                java.util.zip.ZipEntry fmj = zf.getEntry("fabric.mod.json");
                assertNotNull(fmj, "Every active jar in lads-smp-26.3-dev must have a valid fabric.mod.json: " + jarPath.getFileName());
                try (var reader = new java.io.InputStreamReader(zf.getInputStream(fmj), java.nio.charset.StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    String id = obj.get("id").getAsString();
                    String prev = primaryModIdToJar.put(id, jarPath.getFileName().toString());
                    assertNull(prev, "Duplicate top-level mod ID '" + id + "' between " + prev + " and " + jarPath.getFileName());
                    providedIds.add(id);

                    if (obj.has("provides") && obj.get("provides").isJsonArray()) {
                        for (JsonElement p : obj.getAsJsonArray("provides")) {
                            providedIds.add(p.getAsString());
                        }
                    }
                    if (obj.has("jars") && obj.get("jars").isJsonArray()) {
                        for (JsonElement j : obj.getAsJsonArray("jars")) {
                            String nestedPath = j.getAsJsonObject().get("file").getAsString();
                            java.util.zip.ZipEntry nestedEntry = zf.getEntry(nestedPath);
                            if (nestedEntry != null) {
                                try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(zf.getInputStream(nestedEntry))) {
                                    java.util.zip.ZipEntry ne;
                                    while ((ne = zis.getNextEntry()) != null) {
                                        if (ne.getName().equals("fabric.mod.json")) {
                                            JsonObject nestedObj = JsonParser.parseReader(new java.io.InputStreamReader(zis, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                                            providedIds.add(nestedObj.get("id").getAsString());
                                            if (nestedObj.has("provides") && nestedObj.get("provides").isJsonArray()) {
                                                for (JsonElement np : nestedObj.getAsJsonArray("provides")) {
                                                    providedIds.add(np.getAsString());
                                                }
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (obj.has("depends") && obj.get("depends").isJsonObject()) {
                        modDepends.put(id, new java.util.ArrayList<>(obj.getAsJsonObject("depends").keySet()));
                    }
                }
            }
        }

        // Verify every single dependency of every active mod is satisfied
        for (var entry : modDepends.entrySet()) {
            String modId = entry.getKey();
            for (String dep : entry.getValue()) {
                if (dep.startsWith("fabric-")) continue; // Provided by Fabric API modules
                assertTrue(providedIds.contains(dep),
                        "Active mod '" + modId + "' is missing required dependency '" + dep + "'");
            }
        }
        System.out.printf("[LadsSMP Dev-Instance Verification] Verified %d active jars, %d total provided mod IDs (including nested JAR-in-JARs), 0 duplicate mod IDs, 0 missing dependencies.%n",
                jars.size(), providedIds.size());
    }
}
