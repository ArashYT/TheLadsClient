package com.thelads.smp.features.arise.nb1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Isolated configuration manager for Arise Native Batch NB1.
 * Stored at config/ladssmp/arise-nb1.json.
 */
public final class AriseNB1Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP-AriseNB1Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "ladssmp", "arise-nb1.json");
    private static AriseNB1Config INSTANCE = new AriseNB1Config();

    public static AriseNB1Config get() {
        return INSTANCE;
    }

    public static void setInstanceForTesting(AriseNB1Config config) {
        INSTANCE = config;
    }

    // 1. Admin Claims
    public AdminClaimsConfig adminClaims = new AdminClaimsConfig();
    // 2. Carry On Extend
    public CarryOnExtendConfig carryOnExtend = new CarryOnExtendConfig();
    // 3. Complete Turtle Armor
    public CompleteTurtleArmorConfig completeTurtleArmor = new CompleteTurtleArmorConfig();
    // 4. Custom Item Despawn Duration
    public CustomItemDespawnConfig customItemDespawn = new CustomItemDespawnConfig();
    // 5. Dimension Access Manager
    public DimensionAccessConfig dimensionAccess = new DimensionAccessConfig();
    // 6. Enhanced Nature quicksand
    public QuicksandConfig quicksand = new QuicksandConfig();
    // 7. Extra dungeons
    public ExtraDungeonsConfig extraDungeons = new ExtraDungeonsConfig();
    // 8. Faster Portals
    public FasterPortalsConfig fasterPortals = new FasterPortalsConfig();
    // 9. Forgiving Border
    public ForgivingBorderConfig forgivingBorder = new ForgivingBorderConfig();
    // 10. GameRule Manager
    public GameRuleManagerConfig gameruleManager = new GameRuleManagerConfig();
    // 11. Weaker day zombie
    public WeakerDayZombieConfig weakerDayZombie = new WeakerDayZombieConfig();
    // 12. Why stacks of 16?
    public WhyStacksOf16Config whyStacksOf16 = new WhyStacksOf16Config();
    // 13. You Shall Not Spawn!
    public YouShallNotSpawnConfig youShallNotSpawn = new YouShallNotSpawnConfig();
    // 14. SimpleVoiceChat Broadcast
    public VoicechatBroadcastConfig voicechatBroadcast = new VoicechatBroadcastConfig();
    // 15. Useful Clipboard
    public UsefulClipboardConfig usefulClipboard = new UsefulClipboardConfig();

    public static class AdminClaimsConfig {
        public boolean enabled = true;
        public boolean protectExplosions = true;
        public int requiredPermissionLevel = 2;
    }

    public static class CarryOnExtendConfig {
        public boolean enabled = true;
        public float throwPower = 1.5f;
    }

    public static class CompleteTurtleArmorConfig {
        public boolean enabled = true;
        public boolean fullSetDolphinsGrace = true;
        public boolean individualWaterBreathing = true;
    }

    public static class CustomItemDespawnConfig {
        public boolean enabled = true;
        public boolean modifyDefaultItemDuration = false;
        public int defaultItemDuration = 300; // seconds (matches Arise default)
        public Map<String, Integer> despawnDurations = new HashMap<>(); // itemId -> duration in seconds
    }

    public static class DimensionAccessConfig {
        public boolean enabled = true;
    }

    public static class QuicksandConfig {
        public boolean enabled = true;
        public boolean generateInWorld = true;
        public double sinkSpeed = 0.25d;
    }

    public static class ExtraDungeonsConfig {
        public boolean enabled = true;
    }

    public static class FasterPortalsConfig {
        public boolean enabled = true;
        public int portalWaitTime = 10; // survival wait ticks (vanilla is 80)
    }

    public static class ForgivingBorderConfig {
        public boolean enabled = true;
        public double bounceBuffer = 2.0d;
    }

    public static class GameRuleManagerConfig {
        public boolean enabled = true;
        public Map<String, String> forcedRules = new LinkedHashMap<>(); // matches Arise the_ruler.json default ({})
    }

    public static class WeakerDayZombieConfig {
        public boolean enabled = true;
        public int slownessDuration = 100;
        public int weaknessDuration = 100;
        public int slownessAmplifier = 0; // Slowness I
        public int weaknessAmplifier = 0; // Weakness I
    }

    public static class WhyStacksOf16Config {
        public boolean enabled = true;
        public int stackSize = 64;
        public List<String> items = new ArrayList<>(List.of(
                "minecraft:snowball",
                "minecraft:egg",
                "minecraft:ender_pearl",
                "minecraft:honey_bottle",
                "minecraft:bucket"
        ));
    }

    public static class YouShallNotSpawnConfig {
        public boolean enabled = true;
        public List<SpawnRule> dimensions = new ArrayList<>(List.of(
                new SpawnRule("minecraft:zombie", "minecraft:overworld", 1.0)
        ));

        public static class SpawnRule {
            public String entityId;
            public String dimension;
            public double spawn_chance;

            public SpawnRule() {}

            public SpawnRule(String entityId, String dimension, double spawn_chance) {
                this.entityId = entityId;
                this.dimension = dimension;
                this.spawn_chance = spawn_chance;
            }
        }
    }

    public static class VoicechatBroadcastConfig {
        public boolean enabled = true;
        public String broadcastGroupName = "broadcast";
        public int permissionLevel = 2;
    }

    public static class UsefulClipboardConfig {
        public boolean enabled = true;
        public int maxCharacters = 8192;
    }

    public static AriseNB1Config load() {
        return load(CONFIG_PATH);
    }

    public static AriseNB1Config load(Path path) {
        if (!Files.exists(path)) {
            INSTANCE = new AriseNB1Config();
            save(path, INSTANCE);
            return INSTANCE;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            AriseNB1Config config = GSON.fromJson(reader, AriseNB1Config.class);
            INSTANCE = config != null ? config : new AriseNB1Config();
        } catch (Exception e) {
            LOGGER.error("Failed to load Arise NB1 config from {}, using defaults", path, e);
            INSTANCE = new AriseNB1Config();
        }
        return INSTANCE;
    }

    public static void save() {
        save(CONFIG_PATH, INSTANCE);
    }

    public static void save(Path path, AriseNB1Config config) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save Arise NB1 config to {}", path, e);
        }
    }
}
