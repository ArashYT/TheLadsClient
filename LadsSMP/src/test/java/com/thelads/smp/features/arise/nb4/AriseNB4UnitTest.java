package com.thelads.smp.features.arise.nb4;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit verification tests for Native Batch NB4:
 * - AriseNB4Config defaults and round-trip serialization
 * - Food properties and nutritional profiles
 * - Advancement data integrity (schema, parent hierarchy, tiered XP rewards, vanilla backgrounds)
 * - Farmer's Delight recipe wrappers (fabric:load_conditions with farmersdelight)
 * - 16x16 pixel-perfect textures, model JSONs, and localization coverage for all 38 items
 */
public class AriseNB4UnitTest {

    @TempDir
    Path tempDir;

    @Test
    void testConfigDefaultsAndRoundTrip() throws Exception {
        AriseNB4Config cfg = new AriseNB4Config();
        assertTrue(cfg.advancementsEnabled, "Advancements should default to enabled");
        assertEquals(1.0, cfg.advancementXpMultiplier, 1e-6);
        assertEquals(40, cfg.skipTicksAdvancements, "Arise default for skipTicksAdvancements is 40");
        assertTrue(cfg.foodsEnabled, "Foods should default to enabled");
        assertTrue(cfg.melonJuicing, "Arise default for melonJuicing is true");
        assertFalse(cfg.giveSlicedDirectly, "Arise default for giveSlicedDirectly is false");
        assertTrue(cfg.butcheryTallowDrops);
        assertEquals(0.25, cfg.tallowDropChance, 1e-6);

        // Test JSON round-trip
        Gson gson = new Gson();
        Path cfgFile = tempDir.resolve("arise-nb4.json");
        try (Writer w = Files.newBufferedWriter(cfgFile, StandardCharsets.UTF_8)) {
            gson.toJson(cfg, w);
        }

        try (Reader r = Files.newBufferedReader(cfgFile, StandardCharsets.UTF_8)) {
            AriseNB4Config loaded = gson.fromJson(r, AriseNB4Config.class);
            assertNotNull(loaded);
            assertTrue(loaded.advancementsEnabled);
            assertEquals(40, loaded.skipTicksAdvancements);
            assertTrue(loaded.melonJuicing);
            assertFalse(loaded.giveSlicedDirectly);
            assertEquals(0.25, loaded.tallowDropChance, 1e-6);
        }
    }

    @Test
    void testFoodPropertiesAndNutrition() {
        assertEquals(8, AriseNB4Foods.HAM_SANDWICH.nutrition());
        assertEquals(12.8f, AriseNB4Foods.HAM_SANDWICH.saturation(), 1e-4);

        assertEquals(11, AriseNB4Foods.VENISON_STEW.nutrition());
        assertEquals(19.8f, AriseNB4Foods.VENISON_STEW.saturation(), 1e-4);

        assertEquals(3, AriseNB4Foods.CITRUS_MOJITO.nutrition());
        assertTrue(AriseNB4Foods.CITRUS_MOJITO.canAlwaysEat(), "Beverages should be always consumable");

        assertEquals(1, AriseNB4Foods.ANIMAL_TALLOW.nutrition());
        assertEquals(0.2f, AriseNB4Foods.ANIMAL_TALLOW.saturation(), 1e-4);

        assertEquals(11, AriseNB4Foods.BISON_BURGER.nutrition());
        assertEquals(20.9f, AriseNB4Foods.BISON_BURGER.saturation(), 1e-4);
    }

    @Test
    void testAllAdvancementsValidAndTiered() throws Exception {
        String[] categories = {"mining", "combat", "farming", "exploration", "nether", "end", "building", "mods"};
        Set<String> allAdvancementIds = new HashSet<>();
        int totalAdvancements = 0;

        // First pass: collect all advancement IDs
        for (String cat : categories) {
            String pathPrefix = "/data/ladssmp/advancement/" + cat + "/";
            // Check root
            assertNotNull(getClass().getResourceAsStream(pathPrefix + "root.json"), "Missing root for category: " + cat);
            allAdvancementIds.add("ladssmp:" + cat + "/root");
        }

        // Second pass: inspect every JSON
        for (String cat : categories) {
            Path catPath = Path.of("src/main/resources/data/ladssmp/advancement/" + cat);
            if (!Files.isDirectory(catPath)) {
                // In test execution, try relative to current dir or user dir
                catPath = Path.of("LadsSMP/src/main/resources/data/ladssmp/advancement/" + cat);
            }
            if (!Files.isDirectory(catPath)) {
                continue;
            }

            try (var stream = Files.list(catPath)) {
                for (Path advFile : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                    totalAdvancements++;
                    String advName = advFile.getFileName().toString().replace(".json", "");
                    allAdvancementIds.add("ladssmp:" + cat + "/" + advName);

                    try (Reader reader = Files.newBufferedReader(advFile, StandardCharsets.UTF_8)) {
                        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                        assertTrue(obj.has("display"), "Advancement must have display: " + advFile);
                        JsonObject display = obj.getAsJsonObject("display");

                        assertTrue(display.has("icon"), "Advancement must have icon: " + advFile);
                        assertTrue(display.has("title"), "Advancement must have title: " + advFile);
                        assertTrue(display.has("description"), "Advancement must have description: " + advFile);
                        assertTrue(display.has("frame"), "Advancement must have frame: " + advFile);

                        if (advName.equals("root")) {
                            assertTrue(display.has("background"), "Root advancement must have background texture: " + advFile);
                            assertFalse(obj.has("parent"), "Root advancement must not have a parent: " + advFile);
                        } else {
                            assertTrue(obj.has("parent"), "Child advancement must have a parent: " + advFile);
                            String parent = obj.get("parent").getAsString();
                            assertTrue(parent.startsWith("ladssmp:" + cat + "/"), "Parent must be in category: " + parent);
                        }

                        assertTrue(obj.has("criteria"), "Advancement must have criteria: " + advFile);
                        assertTrue(obj.has("requirements"), "Advancement must have requirements: " + advFile);

                        if (obj.has("rewards")) {
                            JsonObject rewards = obj.getAsJsonObject("rewards");
                            if (rewards.has("experience")) {
                                int xp = rewards.get("experience").getAsInt();
                                assertTrue(xp > 0, "XP reward must be positive: " + advFile);
                                String frame = display.get("frame").getAsString();
                                if ("challenge".equals(frame)) {
                                    assertTrue(xp >= 500, "Challenge advancements should reward at least 500 XP: " + advFile);
                                }
                            }
                        }
                    }
                }
            }
        }

        assertTrue(totalAdvancements >= 150, "Should have created at least 150 advancements; found: " + totalAdvancements);
        assertTrue(totalAdvancements <= 250, "Should not exceed 250 advancements; found: " + totalAdvancements);
    }

    @Test
    void testAllFoodRecipesHaveFabricLoadConditions() throws Exception {
        String[] types = {"cooking", "cutting", "crafting"};
        int totalRecipes = 0;

        for (String type : types) {
            Path recipePath = Path.of("src/main/resources/data/ladssmp/recipe/" + type);
            if (!Files.isDirectory(recipePath)) {
                recipePath = Path.of("LadsSMP/src/main/resources/data/ladssmp/recipe/" + type);
            }
            if (!Files.isDirectory(recipePath)) {
                continue;
            }

            try (var stream = Files.list(recipePath)) {
                for (Path recFile : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                    totalRecipes++;
                    try (Reader r = Files.newBufferedReader(recFile, StandardCharsets.UTF_8)) {
                        JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                        assertTrue(obj.has("fabric:load_conditions"), "Every recipe must be wrapped in fabric:load_conditions: " + recFile);
                        JsonArray conditions = obj.getAsJsonArray("fabric:load_conditions");
                        assertTrue(conditions.size() > 0);
                        JsonObject cond = conditions.get(0).getAsJsonObject();
                        assertEquals("fabric:all_mods_loaded", cond.get("condition").getAsString());
                        JsonArray values = cond.getAsJsonArray("values");
                        assertTrue(values.contains(JsonParser.parseString("\"farmersdelight\"")));
                    }
                }
            }
        }

        assertTrue(totalRecipes >= 35, "Should have generated at least 35 FD cooking/cutting/crafting recipes; found: " + totalRecipes);
    }

    @Test
    void testAllFoodTexturesAndModelsExistAndAre16x16() throws Exception {
        String[] items = {
                "ham_sandwich", "mutton_sandwich", "cooked_cod_sandwich", "cooked_salmon_sandwich",
                "bacon_and_egg_sandwich", "tomato_and_lettuce_sandwich", "venison_stew", "duck_noodle_soup",
                "roasted_waterfowl", "braised_bushmeat", "clam_chowder", "crab_cakes", "calamari_rings",
                "grilled_tentacle", "braised_sea_pickle", "fugu_roll", "guardian_soup", "hoglin_loin",
                "hoglin_sirloin", "strider_slice", "grilled_strider", "nether_skewer", "strider_moss_stew",
                "citrus_mojito", "berry_margarita", "glow_berry_fizz", "nether_blaze_cocktail", "green_tea",
                "azalea_tea", "berry_matcha_latte", "beef_burrito", "chicken_taco", "fish_taco",
                "hearty_salad", "animal_tallow", "bison_patty", "bison_burger", "kangaroo_pasta"
        };

        assertEquals(38, items.length);

        Path baseAssets = Path.of("src/main/resources/assets/ladssmp");
        if (!Files.isDirectory(baseAssets)) {
            baseAssets = Path.of("LadsSMP/src/main/resources/assets/ladssmp");
        }

        Path langFile = baseAssets.resolve("lang/en_us.json");
        assertTrue(Files.exists(langFile), "lang/en_us.json must exist");
        JsonObject langJson;
        try (Reader r = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
            langJson = JsonParser.parseReader(r).getAsJsonObject();
        }

        for (String item : items) {
            Path texPath = baseAssets.resolve("textures/item/" + item + ".png");
            assertTrue(Files.exists(texPath), "Texture missing: " + texPath);
            BufferedImage img = ImageIO.read(texPath.toFile());
            assertNotNull(img, "Failed to decode PNG: " + texPath);
            assertEquals(16, img.getWidth(), "Texture width must be 16: " + texPath);
            assertEquals(16, img.getHeight(), "Texture height must be 16: " + texPath);

            Path modelPath = baseAssets.resolve("models/item/" + item + ".json");
            assertTrue(Files.exists(modelPath), "Item model missing: " + modelPath);

            Path itemDefPath = baseAssets.resolve("items/" + item + ".json");
            assertTrue(Files.exists(itemDefPath), "26.3 items/ definition missing: " + itemDefPath);

            String langKey = "item.ladssmp." + item;
            assertTrue(langJson.has(langKey), "Missing translation key in en_us.json: " + langKey);
        }
    }
}
