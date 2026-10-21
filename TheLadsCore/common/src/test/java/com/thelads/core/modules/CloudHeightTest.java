package com.thelads.core.modules;

import com.google.gson.JsonObject;
import com.thelads.core.client.gui.SmoothScroll;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class CloudHeightTest {

    private File tempConfigFile;

    @BeforeEach
    void setUp() throws Exception {
        tempConfigFile = Files.createTempFile("lads-config-test-", ".json").toFile();
        tempConfigFile.deleteOnExit();
        ConfigManager.setTestConfigFile(tempConfigFile);
        ModuleManager.getInstance().resetToDefaults();
    }

    @AfterEach
    void tearDown() {
        ConfigManager.setTestConfigFile(null);
        if (tempConfigFile != null && tempConfigFile.exists()) {
            tempConfigFile.delete();
        }
        ModuleManager.getInstance().resetToDefaults();
    }

    @Test
    void cloudHeightDefaultsAndSliderBounds() {
        CloudHeightModule module = (CloudHeightModule) ModuleManager.getInstance().getModule(CloudHeightModule.NAME);
        assertNotNull(module, "Cloud Height module must be registered");
        assertTrue(module.isEnabled(), "Cloud Height must be enabled by default");
        assertEquals(CloudHeightModule.MIN_Y, module.height.getMin(), 0.001);
        assertEquals(CloudHeightModule.MAX_Y, module.height.getMax(), 0.001);
        assertEquals(CloudHeightModule.STEP, module.height.getStep(), 0.001);
        assertEquals(CloudHeightModule.MAX_VALUE, module.height.getValue(), 0.001);
        assertEquals("MAX", module.height.display());
    }

    @Test
    void sliderDisplayAndParsing() {
        CloudHeightModule module = new CloudHeightModule();
        module.height.setValue(CloudHeightModule.MAX_Y);
        assertEquals("MAX", module.height.display());

        module.height.setValue(192);
        assertEquals("192", module.height.display());

        module.height.setValue(64);
        assertEquals("64", module.height.display());

        // Parsing "MAX" (case insensitive)
        OptionalDouble parsedMax = module.height.parse("MAX");
        assertTrue(parsedMax.isPresent());
        assertEquals(CloudHeightModule.MAX_Y, parsedMax.getAsDouble(), 0.001);

        OptionalDouble parsedMaxLower = module.height.parse("max");
        assertTrue(parsedMaxLower.isPresent());
        assertEquals(CloudHeightModule.MAX_Y, parsedMaxLower.getAsDouble(), 0.001);

        OptionalDouble parsedNumber = module.height.parse("128");
        assertTrue(parsedNumber.isPresent());
        assertEquals(128.0, parsedNumber.getAsDouble(), 0.001);

        OptionalDouble parsedInvalid = module.height.parse("invalid");
        assertFalse(parsedInvalid.isPresent());
    }

    @Test
    void heightResolutionMapping() {
        // When slider is at MAX (256):
        // 1.8.9 world height (256) -> resolves to 256
        assertEquals(256.0f, CloudHeightModule.resolveHeight(256, 256), 0.001f);
        // Modern Overworld (320) -> resolves to 320
        assertEquals(320.0f, CloudHeightModule.resolveHeight(256, 320), 0.001f);
        // Datapack with custom height (512) -> resolves to 512
        assertEquals(512.0f, CloudHeightModule.resolveHeight(256, 512), 0.001f);
        // Custom world with smaller height (128) -> resolves to 128
        assertEquals(128.0f, CloudHeightModule.resolveHeight(256, 128), 0.001f);

        // When slider is at an explicit value (e.g. 192 or 80):
        assertEquals(192.0f, CloudHeightModule.resolveHeight(192, 320), 0.001f);
        assertEquals(80.0f, CloudHeightModule.resolveHeight(80, 256), 0.001f);
        assertEquals(64.0f, CloudHeightModule.resolveHeight(64, 320), 0.001f);
    }

    @Test
    void getTargetCloudHeightRespectsEnabledAndNaN() {
        CloudHeightModule module = (CloudHeightModule) ModuleManager.getInstance().getModule(CloudHeightModule.NAME);
        assertNotNull(module);
        module.setEnabled(true);
        module.height.setValue(CloudHeightModule.MAX_VALUE);

        // Active: resolves MAX to world height 320
        assertEquals(320.0f, CloudHeightModule.getTargetCloudHeight(192.0f, 320), 0.001f);

        // When vanilla height is Float.NaN (Nether / End), always returns Float.NaN
        assertTrue(Float.isNaN(CloudHeightModule.getTargetCloudHeight(Float.NaN, 320)));

        // When module is disabled, returns vanilla height
        module.setEnabled(false);
        assertEquals(192.0f, CloudHeightModule.getTargetCloudHeight(192.0f, 320), 0.001f);
        assertEquals(128.0f, CloudHeightModule.getTargetCloudHeight(128.0f, 256), 0.001f);
    }

    @Test
    void migrationOverridesCloudHeightToMaxOnce() throws Exception {
        // Pre-1.9.14 config without cloudHeight1914 flag
        JsonObject root = new JsonObject();
        JsonObject hud = new JsonObject();
        hud.addProperty(ConfigManager.PRESET_197_FLAG, true);
        hud.addProperty(ConfigManager.HUD_1910_FLAG, true);
        hud.addProperty(ConfigManager.F3_1911_FLAG, true);
        hud.addProperty(ConfigManager.KEYSTROKES_1912_FLAG, true);
        // cloudHeight1914 is intentionally missing
        root.add("hud", hud);

        Files.writeString(tempConfigFile.toPath(), root.toString());

        CloudHeightModule module = (CloudHeightModule) ModuleManager.getInstance().getModule(CloudHeightModule.NAME);
        module.setEnabled(false);
        module.height.setValue(100);

        ConfigManager.load();

        assertTrue(module.isEnabled(), "Migration must set Cloud Height ON");
        assertEquals(CloudHeightModule.MAX_VALUE, module.height.getValue(), 0.001, "Migration must set height to MAX");

        // Player modifies setting
        module.height.setValue(128);
        ConfigManager.save();

        // Second load must keep player's choice because flag is now present
        ConfigManager.load();
        assertEquals(128.0, module.height.getValue(), 0.001, "Player choice must be preserved after migration");
    }

    @Test
    void migrationSetsMobKillBannerOnWithoutEnablingDisabledModule() throws Exception {
        // Test case 1: KillBanner is disabled by user. Migration sets mobs=true, but module remains DISABLED.
        JsonObject root = new JsonObject();
        JsonObject hud = new JsonObject();
        hud.addProperty(ConfigManager.PRESET_197_FLAG, true);
        hud.addProperty(ConfigManager.HUD_1910_FLAG, true);
        hud.addProperty(ConfigManager.F3_1911_FLAG, true);
        hud.addProperty(ConfigManager.KEYSTROKES_1912_FLAG, true);
        hud.addProperty(ConfigManager.CLOUD_HEIGHT_1914_FLAG, true);
        // mobKillBanner1914 is missing
        root.add("hud", hud);

        JsonObject modules = new JsonObject();
        JsonObject kbJson = new JsonObject();
        kbJson.addProperty("enabled", false);
        JsonObject kbOpts = new JsonObject();
        kbOpts.addProperty("Mobs", false);
        kbJson.add("options", kbOpts);
        modules.add("KillBanner", kbJson);
        root.add("modules", modules);

        Files.writeString(tempConfigFile.toPath(), root.toString());

        ConfigManager.load();

        KillBannerModule kb = (KillBannerModule) ModuleManager.getInstance().getModule("KillBanner");
        assertNotNull(kb);
        assertFalse(kb.isEnabled(), "KillBanner must stay disabled if user had it disabled");
        assertTrue(kb.mobs.get(), "KillBanner mobs option must be migrated to true");

        // Test case 2: KillBanner is enabled. Mobs option is migrated to true.
        kbJson.addProperty("enabled", true);
        Files.writeString(tempConfigFile.toPath(), root.toString());
        ConfigManager.load();
        assertTrue(kb.isEnabled(), "KillBanner stays enabled");
        assertTrue(kb.mobs.get(), "KillBanner mobs option is set to true");
    }

    @Test
    void smoothScrollFactorConstant() {
        assertEquals(1.5, SmoothScroll.FACTOR, 0.0001, "SmoothScroll.FACTOR must be 1.5");
    }
}
