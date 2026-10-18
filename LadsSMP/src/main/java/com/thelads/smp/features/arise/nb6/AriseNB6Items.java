package com.thelads.smp.features.arise.nb6;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry manager for Native Batch NB6: Tide-style fishing overhaul additions.
 * Unconditionally registers 20 raw fish, 20 cooked fish, fishing journal, supply crate, and fireproof line.
 */
public final class AriseNB6Items {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB6Items");
    private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
    private static final Map<String, String> RAW_TO_COOKED = new LinkedHashMap<>();

    // 1. Freshwater (Rivers / Plains / Forests)
    public static Item BASS;
    public static Item COOKED_BASS;
    public static Item BLUEGILL;
    public static Item COOKED_BLUEGILL;
    public static Item CARP;
    public static Item COOKED_CARP;
    public static Item TROUT;
    public static Item COOKED_TROUT;

    // 2. Cold / Frozen Waters
    public static Item GLACIER_COD;
    public static Item COOKED_GLACIER_COD;
    public static Item FROSTBITE_FLOUNDER;
    public static Item COOKED_FROSTBITE_FLOUNDER;

    // 3. Warm / Deep Oceans & Reefs
    public static Item TUNA;
    public static Item COOKED_TUNA;
    public static Item SNAPPER;
    public static Item COOKED_SNAPPER;
    public static Item SAILFISH;
    public static Item COOKED_SAILFISH;

    // 4. Swamps & Mangroves
    public static Item CATFISH;
    public static Item COOKED_CATFISH;
    public static Item BULLHEAD;
    public static Item COOKED_BULLHEAD;

    // 5. Caves & Underground (Y < 0)
    public static Item ABYSSAL_ANGLER;
    public static Item COOKED_ABYSSAL_ANGLER;
    public static Item CAVE_TETRA;
    public static Item COOKED_CAVE_TETRA;

    // 6. Night & Weather Specialties
    public static Item LUNAR_GUPPY;
    public static Item COOKED_LUNAR_GUPPY;
    public static Item STORM_PERCH;
    public static Item COOKED_STORM_PERCH;

    // 7. Nether Waters & Lava
    public static Item MAGMA_MACKEREL;
    public static Item COOKED_MAGMA_MACKEREL;
    public static Item CRIMSON_FANGJAW;
    public static Item COOKED_CRIMSON_FANGJAW;
    public static Item WARPED_GUPPY;
    public static Item COOKED_WARPED_GUPPY;

    // 8. The End Dimension
    public static Item VOID_TETRA;
    public static Item COOKED_VOID_TETRA;
    public static Item CHORUS_DRIFTFISH;
    public static Item COOKED_CHORUS_DRIFTFISH;

    // Special items
    public static Item FISHING_JOURNAL;
    public static Item SUPPLY_CRATE;
    public static Item FIREPROOF_LINE;

    private AriseNB6Items() {}

    private static Item register(String name, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", name));
        properties.setId(key);
        Item item = new Item(properties);
        Registry.register(BuiltInRegistries.ITEM, key, item);
        ITEMS.put(name, item);
        return item;
    }

    private static <T extends Item> T registerCustom(String name, Item.Properties properties, ItemFactory<T> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("ladssmp", name));
        properties.setId(key);
        T item = factory.create(properties);
        Registry.register(BuiltInRegistries.ITEM, key, item);
        ITEMS.put(name, item);
        return item;
    }

    @FunctionalInterface
    public interface ItemFactory<T extends Item> {
        T create(Item.Properties properties);
    }

    public static synchronized void registerAll() {
        if (!ITEMS.isEmpty()) {
            return;
        }

        LOGGER.info("[Arise NB6] Registering 43 fishing overhaul items (20 fish species, cooked variants, journal, crate, line)...");

        // 1. Freshwater
        BASS = register("bass", new Item.Properties().food(AriseNB6Foods.BASS));
        COOKED_BASS = register("cooked_bass", new Item.Properties().food(AriseNB6Foods.COOKED_BASS));
        RAW_TO_COOKED.put("bass", "cooked_bass");

        BLUEGILL = register("bluegill", new Item.Properties().food(AriseNB6Foods.BLUEGILL));
        COOKED_BLUEGILL = register("cooked_bluegill", new Item.Properties().food(AriseNB6Foods.COOKED_BLUEGILL));
        RAW_TO_COOKED.put("bluegill", "cooked_bluegill");

        CARP = register("carp", new Item.Properties().food(AriseNB6Foods.CARP));
        COOKED_CARP = register("cooked_carp", new Item.Properties().food(AriseNB6Foods.COOKED_CARP));
        RAW_TO_COOKED.put("carp", "cooked_carp");

        TROUT = register("trout", new Item.Properties().food(AriseNB6Foods.TROUT));
        COOKED_TROUT = register("cooked_trout", new Item.Properties().food(AriseNB6Foods.COOKED_TROUT));
        RAW_TO_COOKED.put("trout", "cooked_trout");

        // 2. Cold Waters
        GLACIER_COD = register("glacier_cod", new Item.Properties().food(AriseNB6Foods.GLACIER_COD));
        COOKED_GLACIER_COD = register("cooked_glacier_cod", new Item.Properties().food(AriseNB6Foods.COOKED_GLACIER_COD));
        RAW_TO_COOKED.put("glacier_cod", "cooked_glacier_cod");

        FROSTBITE_FLOUNDER = register("frostbite_flounder", new Item.Properties().food(AriseNB6Foods.FROSTBITE_FLOUNDER));
        COOKED_FROSTBITE_FLOUNDER = register("cooked_frostbite_flounder", new Item.Properties().food(AriseNB6Foods.COOKED_FROSTBITE_FLOUNDER));
        RAW_TO_COOKED.put("frostbite_flounder", "cooked_frostbite_flounder");

        // 3. Warm / Deep Ocean
        TUNA = register("tuna", new Item.Properties().food(AriseNB6Foods.TUNA));
        COOKED_TUNA = register("cooked_tuna", new Item.Properties().food(AriseNB6Foods.COOKED_TUNA));
        RAW_TO_COOKED.put("tuna", "cooked_tuna");

        SNAPPER = register("snapper", new Item.Properties().food(AriseNB6Foods.SNAPPER));
        COOKED_SNAPPER = register("cooked_snapper", new Item.Properties().food(AriseNB6Foods.COOKED_SNAPPER));
        RAW_TO_COOKED.put("snapper", "cooked_snapper");

        SAILFISH = register("sailfish", new Item.Properties().food(AriseNB6Foods.SAILFISH));
        COOKED_SAILFISH = register("cooked_sailfish", new Item.Properties().food(AriseNB6Foods.COOKED_SAILFISH));
        RAW_TO_COOKED.put("sailfish", "cooked_sailfish");

        // 4. Swamp & Mangrove
        CATFISH = register("catfish", new Item.Properties().food(AriseNB6Foods.CATFISH));
        COOKED_CATFISH = register("cooked_catfish", new Item.Properties().food(AriseNB6Foods.COOKED_CATFISH));
        RAW_TO_COOKED.put("catfish", "cooked_catfish");

        BULLHEAD = register("bullhead", new Item.Properties().food(AriseNB6Foods.BULLHEAD));
        COOKED_BULLHEAD = register("cooked_bullhead", new Item.Properties().food(AriseNB6Foods.COOKED_BULLHEAD));
        RAW_TO_COOKED.put("bullhead", "cooked_bullhead");

        // 5. Caves & Underground
        ABYSSAL_ANGLER = register("abyssal_angler", new Item.Properties().food(AriseNB6Foods.ABYSSAL_ANGLER));
        COOKED_ABYSSAL_ANGLER = register("cooked_abyssal_angler", new Item.Properties().food(AriseNB6Foods.COOKED_ABYSSAL_ANGLER));
        RAW_TO_COOKED.put("abyssal_angler", "cooked_abyssal_angler");

        CAVE_TETRA = register("cave_tetra", new Item.Properties().food(AriseNB6Foods.CAVE_TETRA));
        COOKED_CAVE_TETRA = register("cooked_cave_tetra", new Item.Properties().food(AriseNB6Foods.COOKED_CAVE_TETRA));
        RAW_TO_COOKED.put("cave_tetra", "cooked_cave_tetra");

        // 6. Night & Storm
        LUNAR_GUPPY = register("lunar_guppy", new Item.Properties().food(AriseNB6Foods.LUNAR_GUPPY));
        COOKED_LUNAR_GUPPY = register("cooked_lunar_guppy", new Item.Properties().food(AriseNB6Foods.COOKED_LUNAR_GUPPY));
        RAW_TO_COOKED.put("lunar_guppy", "cooked_lunar_guppy");

        STORM_PERCH = register("storm_perch", new Item.Properties().food(AriseNB6Foods.STORM_PERCH));
        COOKED_STORM_PERCH = register("cooked_storm_perch", new Item.Properties().food(AriseNB6Foods.COOKED_STORM_PERCH));
        RAW_TO_COOKED.put("storm_perch", "cooked_storm_perch");

        // 7. Nether
        MAGMA_MACKEREL = register("magma_mackerel", new Item.Properties().food(AriseNB6Foods.MAGMA_MACKEREL));
        COOKED_MAGMA_MACKEREL = register("cooked_magma_mackerel", new Item.Properties().food(AriseNB6Foods.COOKED_MAGMA_MACKEREL));
        RAW_TO_COOKED.put("magma_mackerel", "cooked_magma_mackerel");

        CRIMSON_FANGJAW = register("crimson_fangjaw", new Item.Properties().food(AriseNB6Foods.CRIMSON_FANGJAW));
        COOKED_CRIMSON_FANGJAW = register("cooked_crimson_fangjaw", new Item.Properties().food(AriseNB6Foods.COOKED_CRIMSON_FANGJAW));
        RAW_TO_COOKED.put("crimson_fangjaw", "cooked_crimson_fangjaw");

        WARPED_GUPPY = register("warped_guppy", new Item.Properties().food(AriseNB6Foods.WARPED_GUPPY));
        COOKED_WARPED_GUPPY = register("cooked_warped_guppy", new Item.Properties().food(AriseNB6Foods.COOKED_WARPED_GUPPY));
        RAW_TO_COOKED.put("warped_guppy", "cooked_warped_guppy");

        // 8. The End
        VOID_TETRA = register("void_tetra", new Item.Properties().food(AriseNB6Foods.VOID_TETRA));
        COOKED_VOID_TETRA = register("cooked_void_tetra", new Item.Properties().food(AriseNB6Foods.COOKED_VOID_TETRA));
        RAW_TO_COOKED.put("void_tetra", "cooked_void_tetra");

        CHORUS_DRIFTFISH = register("chorus_driftfish", new Item.Properties().food(AriseNB6Foods.CHORUS_DRIFTFISH));
        COOKED_CHORUS_DRIFTFISH = register("cooked_chorus_driftfish", new Item.Properties().food(AriseNB6Foods.COOKED_CHORUS_DRIFTFISH));
        RAW_TO_COOKED.put("chorus_driftfish", "cooked_chorus_driftfish");

        // Special Items
        FISHING_JOURNAL = registerCustom("fishing_journal", new Item.Properties().stacksTo(1), FishingJournalItem::new);
        SUPPLY_CRATE = registerCustom("supply_crate", new Item.Properties().stacksTo(16), SupplyCrateItem::new);
        FIREPROOF_LINE = register("fireproof_line", new Item.Properties().stacksTo(64));
    }

    public static Map<String, Item> getAllItems() {
        return Collections.unmodifiableMap(ITEMS);
    }

    public static Set<String> getRawFishSpecies() {
        return Collections.unmodifiableSet(RAW_TO_COOKED.keySet());
    }

    public static String getCookedVariant(String rawFishId) {
        return RAW_TO_COOKED.get(rawFishId);
    }

    public static Item getItem(String name) {
        return ITEMS.get(name);
    }
}
