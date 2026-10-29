package com.thelads.smp.config;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Central configuration for Lads SMP (config/ladssmp/config.json).
 * Controls 26.3 data compatibility upgrading, exploit protections, and consolidated QoL modules.
 */
public final class LadsConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile LadsConfig INSTANCE = new LadsConfig();

    public CompatSettings compat = new CompatSettings();
    public FeatureSettings features = new FeatureSettings();

    public static final class CompatSettings {
        /** Automatically upgrade 1.20/1.21 loot conditions, loot table 'functions' -> 'modifier', and modded recipes to MC 26.3 format. */
        public boolean upgradeLegacyData = true;
        /** Strip disabled items directly from loot table entries and recipe outputs during registry load. */
        public boolean filterDisabledFromData = true;
        /** Automatically sanitize progression-breaking loot table entries (e.g. Nether Stars in MNS libraries, Overworld Netherite in MTR Ocean Temples, 100% Enchanted Gapples in Slime Caves, 30% Dragon Eggs in RMF). */
        public boolean rebalanceBrokenLootTables = true;
        /** Log summary counts of upgraded loot tables and recipes on world/server load. */
        public boolean logUpgradeSummary = true;
    }

    public static final class FeatureSettings {
        /** Consolidates 'always_eat': Allow eating food even when the hunger bar is full. */
        public boolean alwaysEat = true;
        /** Items excluded from alwaysEat so players do not accidentally waste rare buff consumables unless hungry, or empty to allow all. */
        public List<String> alwaysEatBlacklist = new ArrayList<>(List.of(
                "minecraft:chorus_fruit",
                "minecraft:rotten_flesh",
                "minecraft:spider_eye",
                "minecraft:pufferfish",
                "minecraft:poisonous_potato"
        ));

        /** Consolidates 'noanimaltemptdelay': Remove the 5-second (100-tick) cooldown after tempting animals with food. */
        public boolean noAnimalTemptDelay = true;

        /** Consolidates 'betterspawnercontrol': Disable monster spawners when surrounded on all 4 horizontal sides + top by torches or lanterns. */
        public boolean betterSpawnerControl = true;
        public int spawnerRequiredTorchFaces = 5;

        /** Consolidates 'cryingportals': Allow Crying Obsidian to be used as valid Nether Portal frame blocks. */
        public boolean cryingPortals = true;

        /** Consolidates 'despawningeggshatch': Thrown/dropped chicken eggs that reach despawn age have a chance to hatch into a baby chick. */
        public boolean despawningEggsHatch = true;
        public double eggHatchChance = 0.35;
        public int maxNearbyChickensForHatch = 24;

        /** Consolidates 'doubledoors': Opening/closing a wooden door or fence gate simultaneously toggles its paired adjacent door unless sneaking. */
        public boolean doubleDoors = true;

        /** Consolidates 'friendlyfire': Prevent players from hitting their own or other players' tamed pets unless sneaking. */
        public boolean friendlyFireProtection = true;
        public boolean protectAllPlayersPets = true;
        public boolean showFriendlyFireMessage = true;

        /** Consolidates 'attributefix': Raise vanilla RangedAttribute caps (health, armor, toughness, attack damage) for modded RPG scaling. */
        public boolean attributeFix = true;
        public double maxHealthCap = 1_000_000.0;
        public double maxArmorCap = 1_024.0;
        public double maxArmorToughnessCap = 1_024.0;
        public double maxAttackDamageCap = 1_000_000.0;

        /** Consolidates 'deathbackup': Record a timestamped JSON snapshot of player inventory and equipment upon death for admin recovery. */
        public boolean deathBackup = true;
        public int maxBackupsPerPlayer = 15;

        /** Consolidates 'stackrefill': Automatically refill a depleted hand item stack or broken tool from matching inventory stacks. */
        public boolean stackRefill = true;

        /** Consolidates 'stickyenchantinglapis': Keep Lapis Lazuli stored inside Enchanting Tables when closing the menu. */
        public boolean stickyEnchantingLapis = true;

        /** Consolidates 'villagernames': Assign curated first names to unnamed Villagers when interacted with or loaded. */
        public boolean villagerNames = true;

        /** Consolidates 'letmedespawn': Allow mobs that picked up items or Endermen holding blocks to despawn while dropping picked-up gear. */
        public boolean letMeDespawn = true;

        /** Consolidates 'skeletonaifix': Classic skeleton bow AI (no strafing, distance-scaled firing interval). */
        public boolean skeletonAiFix = true;

        /** Consolidates 'golem_spawn_fix': Allow Iron/Snow Golems to spawn even when grass/flowers/carpets are under their arms, preserving playerCreated=true. */
        public boolean golemSpawnFix = true;

        /** Consolidates 'portalsgui': Prevent Nether Portals from force-closing open GUI screens on the client. */
        public boolean portalsGui = true;

        /** Consolidates 'waystones_teleport_pets': Automatically teleport nearby non-sitting tamed pets with their owner when using a Waystone. */
        public boolean waystonesTeleportPets = true;
        public double waystonesPetSearchRadius = 10.0;

        /** Consolidates 'one-level-renaming': Cap pure item renaming in an anvil to 1 XP level. */
        public boolean oneLevelRenaming = true;

        /** Consolidates 'anti-enderdragon-grief': Prevent Ender Dragon from destroying normal overworld/end building blocks. */
        public boolean antiEnderDragonGrief = true;

        /** Consolidates 'sleep-heal': Apply Regeneration II while sleeping in a bed and clear on wake. */
        public boolean sleepHeal = true;

        /** Consolidates 'no-killer-requirement': Credit closest player on mob death so player-kill loot/XP drops in farms. */
        public boolean noKillerRequirement = true;

        /** Consolidates 'stackablepotions': Allow potions, splash potions, and lingering potions to stack up to potionMaxStackSize. */
        public boolean stackablePotions = true;
        public int potionMaxStackSize = 16;

        /** Consolidates 'noscaffoldingdown': Require looking down (>70 pitch) while sneaking to descend through scaffolding. */
        public boolean noScaffoldingDown = true;

        /** Consolidates 'advancementscreenshot': Automatically take a screenshot when an advancement toast appears on the client. */
        public boolean advancementScreenshot = true;

        /** Consolidates 'betterjoinmessages': Custom join/leave/first-join messages and /bjm command. */
        public boolean betterJoinMessages = true;

        /** Consolidates 'cutthrough': Swing weapons through grass/plants without collision boxes to hit entities behind them. */
        public boolean cutThrough = true;

        /** Consolidates 'leavesbegone': Fast leaf decay (5-20 ticks) when logs are removed. */
        public boolean fastLeafDecay = true;

        /** Consolidates 'netherportalfix': Track return portals per player so exiting a Nether Portal returns to the original portal. */
        public boolean netherPortalFix = true;

        /** Consolidates 'rightclickharvest': Right-click mature crops, nether wart, cocoa, sugar cane, and cactus to harvest & replant. */
        public boolean rightClickHarvest = true;

        /** Consolidates 'zombieimprovements': Leader zombie scale/damage/aura/sounds/prefix and reinforcement spawn sounds. */
        public boolean zombieImprovements = true;
        public boolean zombieLeaderRedAura = true;
        public boolean zombieLeaderIncreasedDamage = true;
        public double zombieLeaderSizeIncrease = 0.125;
        public boolean zombieLeaderUniqueTexture = true;
        public boolean zombieLeaderUniqueSounds = true;
        public boolean zombieLeaderNamePrefix = true;
        public boolean zombieReinforcementSpawnSounds = true;
        public float zombieSpawnSoundVolume = 1.0f;
        public boolean zombieOnlyLeaderSpawnsReinforcements = false;
        public boolean zombieNoLeaderFromSpawners = false;
        public boolean zombieNoReinforcementsFromSpawners = false;
        public float zombieLeaderMaxSpawnChance = 0.05f;

        /** Chest-only music disc loot randomization (adds a small chance for random music discs in structure chests, never block drops). */
        public boolean randomizeChestDiscLoot = true;

        /** Consolidates FTB Ultimine: Native Vein Mining (Batch NB8). */
        public boolean veinMining = true;

        /** Exploit protection: Prevent block-carrying or breaking/moving protected blocks (spawners, vaults, trial spawners, Lootr chests). */
        public boolean carryOnProtection = true;
        public List<String> protectedBlockPatterns = new ArrayList<>(List.of(
                "minecraft:spawner",
                "minecraft:trial_spawner",
                "minecraft:vault",
                "lootr:*",
                "waystones:*",
                "mr_player_deathcorpses:*"
        ));

        /** Consolidates 'nametagtweaks': Name-tag shaped crafting recipe, /nametag command, and named mobs drop their name tag on death. */
        public boolean nameTagTweaks = true;
        public boolean nameTagCommandReplaceUnderscoresWithSpaces = true;

        /** Consolidates 'naturallychargedcreepers': Naturally spawned creepers have a chance to spawn charged. */
        public boolean naturallyChargedCreepers = true;
        public double naturallyChargedCreepersChance = 0.1d;

        /** Consolidates 'nofeathertrample': Farmland is not trampled by an entity wearing Feather Falling or with Slow Falling. */
        public boolean noFeatherTrample = true;

        /** Consolidates 'enchantingcommands': /ec command to enchant held item above vanilla max level. */
        public boolean enchantingCommands = true;

        /** Consolidates 'stonecutter-damage': Stonecutters damage entities standing on them with a custom damage type. */
        public boolean stonecutterDamage = true;

        /** Consolidates 'imfast': relaxes the server's 'moved too quickly' limits 4x to reduce rubberbanding (never unlimited; 'moved wrongly' stays vanilla). Off by default. */
        public boolean noSpeedLimit = false;

        /** Consolidates 'logprot': Invulnerability for N seconds after login/dimension change/respawn until player moves or acts. */
        public boolean loginProtection = true;
        public int loginProtectionSeconds = 50;

        /** Consolidates 'default-server-properties' (dsp): Seeds default server.properties on fresh dedicated server installations. */
        public boolean defaultServerProperties = true;
    }

    public static LadsConfig get() {
        return INSTANCE;
    }

    public static void setInstanceForTesting(LadsConfig config) {
        INSTANCE = config;
    }

    public static Path getDefaultConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("ladssmp").resolve("config.json");
        } catch (Throwable ignored) {
            return Path.of("build", "test-config", "ladssmp", "config.json");
        }
    }

    public static LadsConfig load() {
        return load(getDefaultConfigPath());
    }

    public static synchronized LadsConfig load(Path path) {
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    LadsConfig loaded = GSON.fromJson(reader, LadsConfig.class);
                    if (loaded != null) {
                        if (loaded.compat == null) loaded.compat = new CompatSettings();
                        if (loaded.features == null) loaded.features = new FeatureSettings();
                        INSTANCE = loaded;
                        save(path, loaded);
                        return loaded;
                    }
                }
            }
            LadsConfig defaults = new LadsConfig();
            INSTANCE = defaults;
            save(path, defaults);
            return defaults;
        } catch (Exception e) {
            LOGGER.error("Failed to load Lads SMP config from {}, using defaults", path, e);
            return INSTANCE;
        }
    }

    public static synchronized void save(Path path, LadsConfig config) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save Lads SMP config to {}", path, e);
        }
    }
}
