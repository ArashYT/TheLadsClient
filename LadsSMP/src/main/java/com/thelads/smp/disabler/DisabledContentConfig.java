package com.thelads.smp.disabler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration model for the Comprehensive Item and Content Disabling System
 * stored at config/ladssmp/disabled_items.json.
 */
public final class DisabledContentConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/DisablerConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public enum ExistingItemPolicy {
        /** Remove the item from the player/container, record full snapshot in world/data/ladssmp_quarantine/, and log to audit file. */
        QUARANTINE,
        /** Permanently delete the item and log the removal to the audit file. */
        DELETE,
        /** Replace the disabled item stack with replacementItemId and log the replacement. */
        REPLACE
    }

    /** Master switch for the item and content disabling system. */
    public boolean enabled = true;

    /** Policy applied when a disabled item is found in an existing player inventory, equipment slot, or container. */
    public ExistingItemPolicy existingItemPolicy = ExistingItemPolicy.QUARANTINE;

    /** Replacement item ID used when existingItemPolicy == REPLACE. */
    public String replacementItemId = "minecraft:emerald";

    /** Whether server operators (permission level >= 2) bypass inventory/pickup/command restrictions. Default false for strict SMP parity. */
    public boolean allowAdminBypass = false;

    /** Whether to notify a player in chat/actionbar when a disabled item is quarantined or removed from their inventory. */
    public boolean notifyPlayerOnRemoval = true;

    /** Interval in server ticks between automatic player inventory/equipment sanitization sweeps (default 10 ticks = 0.5s). */
    public int inventorySweepIntervalTicks = 10;

    /**
     * Globally disabled item IDs, glob patterns, or #tags enforced across all 10 pathways:
     * - Exact IDs: "namespace:item_name"
     * - Wildcards / Globs: "namespace:*", "*:*_backpack"
     * - Item Tags: "#namespace:tag_name"
     */
    public List<String> disabledItems = new ArrayList<>(List.of(
            "smartbackpacks:flight_upgrade",
            "smartbackpacks:repair_upgrade",
            "usefulhats:halo",
            "usefulhats:shulker_helmet",
            "relics_artifacts:chrono_watch",
            "femboymod:canvas_backpack",
            "femboymod:leather_backpack",
            "femboymod:netherite_backpack",
            "femboymod:*_backpack"
    ));

    /**
     * Items that are still obtainable as mob drops/trophies,
     * but whose crafting/smelting/smithing/stonecutting recipes are disabled.
     */
    public List<String> recipeOnlyDisabledItems = new ArrayList<>(List.of(
            "alexsmobs:mimicream"
    ));

    /**
     * Items that are blocked from generating in chest/structure/archeology/fishing loot tables,
     * while remaining craftable or obtainable through bosses/mining.
     */
    public List<String> lootOnlyDisabledItems = new ArrayList<>(List.of(
            "minecraft:nether_star",
            "minecraft:dragon_egg"
    ));

    /**
     * Specific recipe IDs (or glob patterns) to disable regardless of output item.
     */
    public List<String> disabledRecipeIds = new ArrayList<>(List.of(
            "alexsmobs:mimicream_repair"
    ));

    /**
     * Human-readable rationale notes for disabled entries (maintained for admin clarity).
     */
    public Map<String, String> disableReasons = new LinkedHashMap<>(Map.of(
            "smartbackpacks:flight_upgrade", "Grants Creative-style flight from a backpack slot, bypassing End/Elytra progression.",
            "smartbackpacks:repair_upgrade", "Passively repairs all items inside the backpack using raw XP, bypassing Mending and Anvil mechanics.",
            "usefulhats:halo", "Grants Creative flight and complete hostile mob immunity for 1 Nether Star + 4 Glowstone.",
            "usefulhats:shulker_helmet", "Grants on-demand Levitation flight from a hat slot.",
            "relics_artifacts:chrono_watch", "Rewinds player position by 5 seconds passively from main inventory without occupying an accessory slot.",
            "femboymod:*_backpack", "Redundant second backpack system alongside Smart Backpacks; prevents nested backpack exploits.",
            "alexsmobs:mimicream", "Recipe disabled to prevent cloning any damaged god-tier enchanted weapon/armor/tool with 8 Mimicream.",
            "minecraft:nether_star", "Loot-only disabled so Nether Stars must be earned by defeating the Wither rather than looted from MNS library chests.",
            "minecraft:dragon_egg", "Loot-only disabled to block regs-more-foods from dropping extra Dragon Eggs via loot tables."
    ));

    public static Path getDefaultPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("ladssmp").resolve("disabled_items.json");
        } catch (Throwable ignored) {
            return Path.of("build", "test-config", "ladssmp", "disabled_items.json");
        }
    }

    public static synchronized DisabledContentConfig load(Path path) {
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    DisabledContentConfig loaded = GSON.fromJson(reader, DisabledContentConfig.class);
                    if (loaded != null) {
                        if (loaded.existingItemPolicy == null) loaded.existingItemPolicy = ExistingItemPolicy.QUARANTINE;
                        if (loaded.replacementItemId == null || loaded.replacementItemId.isBlank()) {
                            loaded.replacementItemId = "minecraft:emerald";
                        }
                        if (loaded.disabledItems == null) loaded.disabledItems = new ArrayList<>();
                        if (loaded.recipeOnlyDisabledItems == null) loaded.recipeOnlyDisabledItems = new ArrayList<>();
                        if (loaded.lootOnlyDisabledItems == null) loaded.lootOnlyDisabledItems = new ArrayList<>();
                        if (loaded.disabledRecipeIds == null) loaded.disabledRecipeIds = new ArrayList<>();
                        if (loaded.disableReasons == null) loaded.disableReasons = new LinkedHashMap<>();
                        save(path, loaded);
                        return loaded;
                    }
                }
            }
            DisabledContentConfig defaults = new DisabledContentConfig();
            save(path, defaults);
            return defaults;
        } catch (Exception e) {
            LOGGER.error("Failed to load disabled_items.json from {}, keeping active configuration", path, e);
            try {
                Path broken = path.resolveSibling(path.getFileName().toString() + ".broken-" + System.currentTimeMillis());
                Files.copy(path, broken, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warn("Corrupt disabled items file backed up to {}", broken);
            } catch (Exception ignored) {}
            return null;
        }
    }

    public static synchronized void save(Path path, DisabledContentConfig config) {
        if (config == null) return;
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Path temp = path.resolveSibling(path.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save disabled_items.json to {}", path, e);
        }
    }
}
