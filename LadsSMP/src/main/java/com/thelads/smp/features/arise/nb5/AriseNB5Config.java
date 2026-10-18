package com.thelads.smp.features.arise.nb5;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Clean-room configuration manager for NB5 (ParCool-style Parkour & Stamina).
 * Preserves the owner's Arise SMP ParCool default values and supports live reload.
 */
public final class AriseNB5Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("LadsSMP/AriseNB5Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "config/ladssmp/arise-nb5.json";
    private static volatile AriseNB5Config instance;

    // Feature Toggles
    public boolean enabled = true;
    public boolean vaultEnabled = true;
    public boolean wallJumpEnabled = true;
    public boolean ledgeGrabEnabled = true;
    public boolean slideEnabled = true;
    public boolean dodgeRollEnabled = true;
    public boolean breakfallEnabled = true;
    public boolean staminaEnabled = true;

    // Stamina Settings (Arise ParCool Defaults)
    public int maxStamina = 1000;
    public int staminaRecoveryRate = 12;
    public int vaultStaminaCost = 50;
    public int wallJumpStaminaCost = 120;
    public int ledgeClimbStaminaCost = 150;
    public int ledgeHangStaminaCostPerTick = 2;
    public int dodgeStaminaCost = 80;
    public int rollStaminaCost = 100;
    public int breakfallStaminaCost = 50;
    public boolean exhaustionRecoveryPenalty = true;
    public boolean infiniteStaminaCreative = true;

    // Movement & Cooldown Timings (Arise ParCool Defaults)
    public int dodgeCoolTime = 11;
    public int successiveDodgeCoolTime = 30;
    public int successiveDodgeCount = 3;
    public int slidingContinuableTick = 15;
    public float acceptableAngleWallJump = 110.0f;
    public float lowestFallDistanceForBreakfall = 2.0f;
    public float breakfallFallReduction = 5.0f;
    public float dodgeSpeedModifier = 1.0f;

    // HUD Configuration (Arise ParCool Defaults)
    public String staminaHudType = "Light";
    public String alignH = "Right";
    public String alignV = "Bottom";
    public int offsetHLightHud = 0;
    public int offsetVLightHud = -6;
    public boolean hideHudIfStaminaFull = true;

    // Visuals & Animations
    public boolean enableAnimations = true;
    public boolean enableCameraRotationRoll = true;

    public static AriseNB5Config get() {
        if (instance == null) {
            synchronized (AriseNB5Config.class) {
                if (instance == null) {
                    instance = load();
                }
            }
        }
        return instance;
    }

    public static synchronized AriseNB5Config load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                AriseNB5Config cfg = GSON.fromJson(reader, AriseNB5Config.class);
                if (cfg != null) {
                    instance = cfg;
                    return cfg;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to read {}: reverting to defaults", path, e);
            }
        }
        AriseNB5Config fresh = new AriseNB5Config();
        fresh.save();
        instance = fresh;
        return fresh;
    }

    public synchronized void save() {
        Path path = getConfigPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save config to {}", path, e);
        }
    }

    private static Path getConfigPath() {
        try {
            return FabricLoader.getInstance().getGameDir().resolve(FILE_NAME);
        } catch (Throwable t) {
            return Path.of(FILE_NAME);
        }
    }
}
