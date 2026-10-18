package com.thelads.smp.features.arise.nb3;

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
 * Configuration manager for 1.9.11 native batch NB3 (config/ladssmp/arise-nb3.json).
 * Preserves the Arise SMP pack owner's default configuration values.
 */
public final class AriseNb3Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB3Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile AriseNb3Config INSTANCE = new AriseNb3Config();

    public BackWeaponSettings backWeapon = new BackWeaponSettings();
    public CosmeticArmorSettings cosmeticArmor = new CosmeticArmorSettings();
    public HoldMyItemsSettings holdMyItems = new HoldMyItemsSettings();
    public ImmersiveUISettings immersiveUI = new ImmersiveUISettings();
    public TargetDummySettings targetDummy = new TargetDummySettings();
    public ObscureTooltipsSettings obscureTooltips = new ObscureTooltipsSettings();
    public SnailMailSettings snailMail = new SnailMailSettings();
    public BotanyPotsSettings botanyPots = new BotanyPotsSettings();

    public static final class BackWeaponSettings {
        public boolean enabled = true;
        public boolean showBackWeapon = true;
        public int xOffset = 0;
        public int yOffset = 0;
        public boolean disallowCurioItems = true;
        public boolean allowSwapping = true;
    }

    public static final class CosmeticArmorSettings {
        public boolean enabled = true;
        public boolean cosArmorKeepThroughDeath = false;
        public int guiButtonLeft = 65;
        public int guiButtonTop = 67;
        public int toggleButtonLeft = 59;
        public int toggleButtonTop = 72;
        public boolean hideButton = false;
    }

    public static final class HoldMyItemsSettings {
        public boolean enabled = true;
        public float animationSpeed = 1.0f;
        public float switchSpeed = 0.018f;
        public boolean enableSwimmingAnimation = true;
        public int swingSpeed = 9;
        public boolean enableClimbAndCrawlAnimation = true;
        public boolean enablePunchingAnimation = true;
        public float viewmodelXOffset = 0.0f;
        public float viewmodelYOffset = 0.0f;
        public float viewmodelZOffset = 0.0f;
        public List<String> excludedItemIds = new ArrayList<>(List.of(
                "minecraft:compass",
                "minecraft:clock"
        ));
    }

    public static final class ImmersiveUISettings {
        public boolean enabled = true;
        public boolean enableHotbarSelectorAnimation = true;
        public float hotbarSelectorSpeed = 3.0f;
        public boolean enableFloatingItemRotation = true;
        public float floatingItemEasingSpeed = 0.75f;
        public float floatingItemRotationAmplitude = 1.0f;
        public float hoveredItemScale = 1.4f;
        public boolean enableMatchingItemHovering = true;
        public float matchingItemHoverAmplitude = 0.8f;
        public boolean enableScreenShake = true;
        public float shakeAmplitude = 1.25f;
        public int shakeTimer = 8;
    }

    public static final class TargetDummySettings {
        public boolean enabled = true;
        public boolean damageNumbers = true;
        public float animationIntensity = 0.75f;
        public boolean fullBrightDamageNumbers = true;
        public String critMode = "COLOR_AND_MULTIPLIER";
        public boolean hayParticles = true;
        public String dpsMode = "DYNAMIC";
        public boolean disableEquipmentDamage = true;
        public boolean dummyDecoy = true;
        public int scarecrowRadius = 12;
    }

    public static final class ObscureTooltipsSettings {
        public boolean enabled = true;
        public boolean displayArmorModels = true;
        public boolean displayToolsModels = true;
    }

    public static final class SnailMailSettings {
        public boolean enabled = true;
        public boolean lockBoxes = true;
        public boolean protectBoxDestroy = true;
        public boolean opBypassLock = true;
        public int bypassLockOpLevel = 3;
        public int deliveryDelayMinutes = 5;
    }

    public static final class BotanyPotsSettings {
        public boolean enabled = true;
        public int growthIntervalTicks = 200;
        public int hopperExportIntervalTicks = 40;
        public boolean enableHopperPots = true;
    }

    public static AriseNb3Config get() {
        return INSTANCE;
    }

    public static void setInstanceForTesting(AriseNb3Config config) {
        INSTANCE = config;
    }

    public static Path getDefaultConfigPath() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("ladssmp").resolve("arise-nb3.json");
        } catch (Throwable ignored) {
            return Path.of("build", "test-config", "ladssmp", "arise-nb3.json");
        }
    }

    public static AriseNb3Config load() {
        return load(getDefaultConfigPath());
    }

    public static synchronized AriseNb3Config load(Path path) {
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    AriseNb3Config loaded = GSON.fromJson(reader, AriseNb3Config.class);
                    if (loaded != null) {
                        if (loaded.backWeapon == null) loaded.backWeapon = new BackWeaponSettings();
                        if (loaded.cosmeticArmor == null) loaded.cosmeticArmor = new CosmeticArmorSettings();
                        if (loaded.holdMyItems == null) loaded.holdMyItems = new HoldMyItemsSettings();
                        if (loaded.immersiveUI == null) loaded.immersiveUI = new ImmersiveUISettings();
                        if (loaded.targetDummy == null) loaded.targetDummy = new TargetDummySettings();
                        if (loaded.obscureTooltips == null) loaded.obscureTooltips = new ObscureTooltipsSettings();
                        if (loaded.snailMail == null) loaded.snailMail = new SnailMailSettings();
                        if (loaded.botanyPots == null) loaded.botanyPots = new BotanyPotsSettings();
                        INSTANCE = loaded;
                        save(path, loaded);
                        return loaded;
                    }
                }
            }
            AriseNb3Config defaults = new AriseNb3Config();
            INSTANCE = defaults;
            save(path, defaults);
            return defaults;
        } catch (Exception e) {
            LOGGER.error("Failed to load Arise NB3 config from {}, using defaults", path, e);
            return INSTANCE;
        }
    }

    public static synchronized void save(Path path, AriseNb3Config config) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save Arise NB3 config to {}", path, e);
        }
    }
}
