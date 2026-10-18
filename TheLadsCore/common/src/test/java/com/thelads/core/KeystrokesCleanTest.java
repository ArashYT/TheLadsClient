package com.thelads.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hud.KeystrokesHudElement;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.KeystrokesModule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class KeystrokesCleanTest {
    @TempDir Path temp;
    private JsonObject before;

    private record Fill(int left, int top, int right, int bottom) {}
    private record Text(String value, int x, int y, int color, boolean shadow) {}

    private static class RecordingGraphics extends LadsGraphicsTest.MockGraphics {
        final List<Fill> fills = new ArrayList<>();
        final List<Text> texts = new ArrayList<>();

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            super.fill(left, top, right, bottom, color);
            fills.add(new Fill(left, top, right, bottom));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            super.drawText(text, x, y, color, shadow);
            texts.add(new Text(text, x, y, color, shadow));
        }
    }

    private final RecordingGraphics graphics = new RecordingGraphics();

    @BeforeEach
    void setup() {
        before = ConfigManager.toJson();
        ConfigManager.setTestConfigFile(temp.resolve("settings.json").toFile());
        LadsGameBridge.set(new DefaultGameBridge());
        graphics.width = 640;
        graphics.height = 360;
    }

    @AfterEach
    void restore() {
        ConfigManager.applyJson(before);
        ConfigManager.setTestConfigFile(null);
    }

    @Test
    void defaultStyleIsClean() {
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        assertNotNull(module);
        assertEquals(KeystrokesModule.STYLE_CLEAN, module.style.getIndex());
        assertEquals("Clean", module.style.getValue());
        assertTrue(module.isClean());
    }

    @Test
    void cleanLayoutDimensionsMatchSpecification() {
        KeystrokesHudElement keys = new KeystrokesHudElement();
        keys.setModuleName("Keystrokes");
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        module.style.setIndex(KeystrokesModule.STYLE_CLEAN);

        // 1. Space + Sneak/Sprint: 67 x 95
        module.showSpace.set(true);
        module.showSneakSprintRow.set(true);
        keys.prepareRender(graphics, false);
        assertEquals(67, keys.getWidth());
        assertEquals(95, keys.getHeight());

        // 2. Space only: 67 x 78
        module.showSpace.set(true);
        module.showSneakSprintRow.set(false);
        keys.prepareRender(graphics, false);
        assertEquals(67, keys.getWidth());
        assertEquals(78, keys.getHeight());

        // 3. Sneak/Sprint only: 67 x 78
        module.showSpace.set(false);
        module.showSneakSprintRow.set(true);
        keys.prepareRender(graphics, false);
        assertEquals(67, keys.getWidth());
        assertEquals(78, keys.getHeight());

        // 4. Neither: 67 x 61
        module.showSpace.set(false);
        module.showSneakSprintRow.set(false);
        keys.prepareRender(graphics, false);
        assertEquals(67, keys.getWidth());
        assertEquals(61, keys.getHeight());
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void cleanLayoutFillsAndTextStayWithinBounds(boolean space, boolean sneakSprint) {
        KeystrokesHudElement keys = new KeystrokesHudElement();
        keys.setModuleName("Keystrokes");
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        module.style.setIndex(KeystrokesModule.STYLE_CLEAN);
        module.showSpace.set(space);
        module.showSneakSprintRow.set(sneakSprint);

        graphics.fills.clear();
        graphics.texts.clear();
        keys.prepareRender(graphics, false);
        keys.render(graphics);

        for (Fill fill : graphics.fills) {
            assertTrue(fill.left() >= keys.getX(), "fill.left >= element.getX");
            assertTrue(fill.top() >= keys.getY(), "fill.top >= element.getY");
            assertTrue(fill.right() <= keys.getX() + keys.getWidth(), "fill.right <= element.getX + width");
            assertTrue(fill.bottom() <= keys.getY() + keys.getHeight(), "fill.bottom <= element.getY + height");
        }
        for (Text text : graphics.texts) {
            assertTrue(text.y() + graphics.fontHeight() <= keys.getY() + keys.getHeight(), "text bottom <= element.getY + height");
        }
    }

    @Test
    void optionVisibilityHidesClassicOptionsInCleanMode() {
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        module.style.setIndex(KeystrokesModule.STYLE_CLEAN);

        assertTrue(module.hidden(module.background));
        assertTrue(module.hidden(module.colorMode));
        assertFalse(module.hidden(module.colorPreset));
        assertFalse(module.hidden(module.keyColor));
        assertFalse(module.hidden(module.showSneakSprintRow));

        // When text shadow is off, shadow color options are hidden
        module.keyTextShadow.set(false);
        module.keyPressedTextShadow.set(false);
        assertTrue(module.hidden(module.keyTextShadowColor));
        assertTrue(module.hidden(module.useCustomTextShadowColor));

        // When shadow is on and custom color is on, shadow color option is visible
        module.keyTextShadow.set(true);
        module.useCustomTextShadowColor.set(true);
        assertFalse(module.hidden(module.keyTextShadowColor));

        // In Classic mode, Clean options are hidden
        module.style.setIndex(KeystrokesModule.STYLE_CLASSIC);
        assertTrue(module.hidden(module.showSneakSprintRow));
        assertTrue(module.hidden(module.colorPreset));
        assertTrue(module.hidden(module.keyColor));
        assertFalse(module.hidden(module.background));
        assertFalse(module.hidden(module.colorMode));
    }

    @Test
    void presetApplicationSetsExpectedColors() {
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        module.style.setIndex(KeystrokesModule.STYLE_CLEAN);

        // Preset 0: Classic
        module.colorPreset.setIndex(0);
        module.touch();
        assertEquals(0xFFFFFFFF, module.keyColor.getColor());
        assertEquals(0xAA000000, module.keyBackgroundColor.getColor());
        assertEquals(0xFF000000, module.keyPressedColor.getColor());
        assertEquals(0xAAFFFFFF, module.keyPressedBackgroundColor.getColor());

        // Preset 3: Beehive
        module.colorPreset.setIndex(3);
        module.touch();
        assertEquals(0xFFFFD000, module.keyColor.getColor());
        assertEquals(0xFF000000, module.keyBackgroundColor.getColor());
        assertEquals(0xFF000000, module.keyPressedColor.getColor());
        assertEquals(0xFFFFD000, module.keyPressedBackgroundColor.getColor());

        // Editing color manually shifts preset to Custom (9)
        module.keyColor.setColor(0xFF112233);
        module.touch();
        assertEquals(9, module.colorPreset.getIndex());
        assertEquals("Custom", module.colorPreset.getValue());
    }

    @Test
    void oneTimeMigrationUpgradesOldConfigToCleanAndPreservesClassicAfterwards() {
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");

        // Old config saved before 1.9.12 (no keystrokesStyleMigrated flag in hud object)
        String oldJson = """
            {
              "modules": {
                "Keystrokes": {
                  "enabled": true,
                  "options": {
                    "Show CPS": true,
                    "Show space bar": true
                  }
                }
              },
              "hud": {
                "shadowOnDefault": true,
                "hudFpsCapOnDefault": true,
                "hud1910OverrideApplied": true,
                "betterF31911Applied": true
              }
            }
            """;
        JsonObject parsed = JsonParser.parseString(oldJson).getAsJsonObject();
        assertTrue(ConfigManager.needsMigrationSave(parsed));

        // Applying old config migrates Style to Clean
        ConfigManager.applyJson(parsed);
        assertEquals(KeystrokesModule.STYLE_CLEAN, module.style.getIndex());

        // Saved config now has the migration flag
        JsonObject saved = ConfigManager.toJson();
        assertTrue(saved.getAsJsonObject("hud").has(ConfigManager.KEYSTROKES_1912_FLAG));
        assertTrue(saved.getAsJsonObject("hud").get(ConfigManager.KEYSTROKES_1912_FLAG).getAsBoolean());

        // Player explicitly selects Classic
        module.style.setIndex(KeystrokesModule.STYLE_CLASSIC);
        JsonObject playerSaved = ConfigManager.toJson();

        // Reloading this config preserves the player's choice of Classic
        ConfigManager.applyJson(playerSaved);
        assertEquals(KeystrokesModule.STYLE_CLASSIC, module.style.getIndex());
        assertFalse(ConfigManager.needsMigrationSave(playerSaved));
    }
}
