package com.thelads.smp.features.arise.nb4;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry manager for Native Batch NB4 culinary food and beverage additions.
 * Clean-room re-implementation of high-value foods from Arise SMP FD addons.
 */
public final class AriseNB4Items {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB4Items");
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    // --- Sandwiches (Delightful Sandwiches / Cultural) ---
    public static Item HAM_SANDWICH;
    public static Item MUTTON_SANDWICH;
    public static Item COOKED_COD_SANDWICH;
    public static Item COOKED_SALMON_SANDWICH;
    public static Item BACON_AND_EGG_SANDWICH;
    public static Item TOMATO_AND_LETTUCE_SANDWICH;

    // --- Naturalist & Wildlife Foods (Naturalist Delight) ---
    public static Item VENISON_STEW;
    public static Item DUCK_NOODLE_SOUP;
    public static Item ROASTED_WATERFOWL;
    public static Item BRAISED_BUSHMEAT;
    public static Item CLAM_CHOWDER;
    public static Item CRAB_CAKES;

    // --- Ocean's Culinary (Ocean's Delight) ---
    public static Item CALAMARI_RINGS;
    public static Item GRILLED_TENTACLE;
    public static Item BRAISED_SEA_PICKLE;
    public static Item FUGU_ROLL;
    public static Item GUARDIAN_SOUP;

    // --- Nether's Culinary (Nether's Delight) ---
    public static Item HOGLIN_LOIN;
    public static Item HOGLIN_SIRLOIN;
    public static Item STRIDER_SLICE;
    public static Item GRILLED_STRIDER;
    public static Item NETHER_SKEWER;
    public static Item STRIDER_MOSS_STEW;

    // --- Cocktails & Beverages (Cocktails Delight / Delightful) ---
    public static Item CITRUS_MOJITO;
    public static Item BERRY_MARGARITA;
    public static Item GLOW_BERRY_FIZZ;
    public static Item NETHER_BLAZE_COCKTAIL;
    public static Item GREEN_TEA;
    public static Item AZALEA_TEA;
    public static Item BERRY_MATCHA_LATTE;

    // --- Cultural Specialties (Cultural Delights) ---
    public static Item BEEF_BURRITO;
    public static Item CHICKEN_TACO;
    public static Item FISH_TACO;
    public static Item HEARTY_SALAD;

    // --- Butchery & Creature Addons (Delightful Slaughter / Alex's) ---
    public static Item ANIMAL_TALLOW;
    public static Item BISON_PATTY;
    public static Item BISON_BURGER;
    public static Item KANGAROO_PASTA;

    private AriseNB4Items() {}

    private static Item register(String name, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", name));
        properties.setId(key);
        Item item = new Item(properties);
        Registry.register(BuiltInRegistries.ITEM, key, item);
        ITEMS.put(name, item);
        return item;
    }

    public static synchronized void registerAll() {
        if (!ITEMS.isEmpty()) {
            return;
        }

        LOGGER.info("[Arise NB4] Registering 38 culinary food & beverage items...");

        // 1-6: Sandwiches
        HAM_SANDWICH = register("ham_sandwich", new Item.Properties().food(AriseNB4Foods.HAM_SANDWICH));
        MUTTON_SANDWICH = register("mutton_sandwich", new Item.Properties().food(AriseNB4Foods.MUTTON_SANDWICH));
        COOKED_COD_SANDWICH = register("cooked_cod_sandwich", new Item.Properties().food(AriseNB4Foods.COOKED_COD_SANDWICH));
        COOKED_SALMON_SANDWICH = register("cooked_salmon_sandwich", new Item.Properties().food(AriseNB4Foods.COOKED_SALMON_SANDWICH));
        BACON_AND_EGG_SANDWICH = register("bacon_and_egg_sandwich", new Item.Properties().food(AriseNB4Foods.BACON_AND_EGG_SANDWICH));
        TOMATO_AND_LETTUCE_SANDWICH = register("tomato_and_lettuce_sandwich", new Item.Properties().food(AriseNB4Foods.TOMATO_AND_LETTUCE_SANDWICH));

        // 7-12: Naturalist Wildlife Foods
        VENISON_STEW = register("venison_stew", new Item.Properties().food(AriseNB4Foods.VENISON_STEW).usingConvertsTo(Items.BOWL).stacksTo(16));
        DUCK_NOODLE_SOUP = register("duck_noodle_soup", new Item.Properties().food(AriseNB4Foods.DUCK_NOODLE_SOUP).usingConvertsTo(Items.BOWL).stacksTo(16));
        ROASTED_WATERFOWL = register("roasted_waterfowl", new Item.Properties().food(AriseNB4Foods.ROASTED_WATERFOWL));
        BRAISED_BUSHMEAT = register("braised_bushmeat", new Item.Properties().food(AriseNB4Foods.BRAISED_BUSHMEAT));
        CLAM_CHOWDER = register("clam_chowder", new Item.Properties().food(AriseNB4Foods.CLAM_CHOWDER).usingConvertsTo(Items.BOWL).stacksTo(16));
        CRAB_CAKES = register("crab_cakes", new Item.Properties().food(AriseNB4Foods.CRAB_CAKES));

        // 13-17: Ocean's Culinary
        CALAMARI_RINGS = register("calamari_rings", new Item.Properties().food(AriseNB4Foods.CALAMARI_RINGS));
        GRILLED_TENTACLE = register("grilled_tentacle", new Item.Properties().food(AriseNB4Foods.GRILLED_TENTACLE));
        BRAISED_SEA_PICKLE = register("braised_sea_pickle", new Item.Properties().food(AriseNB4Foods.BRAISED_SEA_PICKLE));
        FUGU_ROLL = register("fugu_roll", new Item.Properties().food(AriseNB4Foods.FUGU_ROLL, AriseNB4Foods.fuguRollConsumable()));
        GUARDIAN_SOUP = register("guardian_soup", new Item.Properties().food(AriseNB4Foods.GUARDIAN_SOUP, AriseNB4Foods.guardianSoupConsumable()).usingConvertsTo(Items.BOWL).stacksTo(16));

        // 18-23: Nether's Culinary
        HOGLIN_LOIN = register("hoglin_loin", new Item.Properties().food(AriseNB4Foods.HOGLIN_LOIN));
        HOGLIN_SIRLOIN = register("hoglin_sirloin", new Item.Properties().food(AriseNB4Foods.HOGLIN_SIRLOIN));
        STRIDER_SLICE = register("strider_slice", new Item.Properties().food(AriseNB4Foods.STRIDER_SLICE));
        GRILLED_STRIDER = register("grilled_strider", new Item.Properties().food(AriseNB4Foods.GRILLED_STRIDER));
        NETHER_SKEWER = register("nether_skewer", new Item.Properties().food(AriseNB4Foods.NETHER_SKEWER, AriseNB4Foods.netherSkewerConsumable()));
        STRIDER_MOSS_STEW = register("strider_moss_stew", new Item.Properties().food(AriseNB4Foods.STRIDER_MOSS_STEW).usingConvertsTo(Items.BOWL).stacksTo(16));

        // 24-30: Cocktails & Teas
        CITRUS_MOJITO = register("citrus_mojito", new Item.Properties().food(AriseNB4Foods.CITRUS_MOJITO, AriseNB4Foods.citrusMojitoConsumable()).usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16));
        BERRY_MARGARITA = register("berry_margarita", new Item.Properties().food(AriseNB4Foods.BERRY_MARGARITA, AriseNB4Foods.berryMargaritaConsumable()).usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16));
        GLOW_BERRY_FIZZ = register("glow_berry_fizz", new Item.Properties().food(AriseNB4Foods.GLOW_BERRY_FIZZ, AriseNB4Foods.glowBerryFizzConsumable()).usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16));
        NETHER_BLAZE_COCKTAIL = register("nether_blaze_cocktail", new Item.Properties().food(AriseNB4Foods.NETHER_BLAZE_COCKTAIL, AriseNB4Foods.netherBlazeCocktailConsumable()).usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16));
        GREEN_TEA = register("green_tea", new Item.Properties().food(AriseNB4Foods.GREEN_TEA, AriseNB4Foods.greenTeaConsumable()).usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16));
        AZALEA_TEA = register("azalea_tea", new Item.Properties().food(AriseNB4Foods.AZALEA_TEA, AriseNB4Foods.azaleaTeaConsumable()).usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16));
        BERRY_MATCHA_LATTE = register("berry_matcha_latte", new Item.Properties().food(AriseNB4Foods.BERRY_MATCHA_LATTE, AriseNB4Foods.berryMatchaLatteConsumable()).usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16));

        // 31-34: Cultural Specialties
        BEEF_BURRITO = register("beef_burrito", new Item.Properties().food(AriseNB4Foods.BEEF_BURRITO));
        CHICKEN_TACO = register("chicken_taco", new Item.Properties().food(AriseNB4Foods.CHICKEN_TACO));
        FISH_TACO = register("fish_taco", new Item.Properties().food(AriseNB4Foods.FISH_TACO));
        HEARTY_SALAD = register("hearty_salad", new Item.Properties().food(AriseNB4Foods.HEARTY_SALAD).usingConvertsTo(Items.BOWL).stacksTo(16));

        // 35-38: Butchery & Wildlife Addons
        ANIMAL_TALLOW = register("animal_tallow", new Item.Properties().food(AriseNB4Foods.ANIMAL_TALLOW));
        BISON_PATTY = register("bison_patty", new Item.Properties().food(AriseNB4Foods.BISON_PATTY));
        BISON_BURGER = register("bison_burger", new Item.Properties().food(AriseNB4Foods.BISON_BURGER));
        KANGAROO_PASTA = register("kangaroo_pasta", new Item.Properties().food(AriseNB4Foods.KANGAROO_PASTA, AriseNB4Foods.kangarooPastaConsumable()).usingConvertsTo(Items.BOWL).stacksTo(16));

        LOGGER.info("[Arise NB4] Successfully registered {} culinary items into ladssmp.", ITEMS.size());
    }

    public static Map<String, Item> getItems() {
        return Collections.unmodifiableMap(ITEMS);
    }
}
