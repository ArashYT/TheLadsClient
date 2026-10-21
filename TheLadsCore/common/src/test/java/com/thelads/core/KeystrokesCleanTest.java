package com.thelads.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout;
import com.thelads.core.client.hud.KeystrokesHudElement;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.KeystrokesModule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private record Fill(int left, int top, int right, int bottom, int color) {}
    private record Text(String value, int x, int y, int color, boolean shadow) {}

    private static class RecordingGraphics extends LadsGraphicsTest.MockGraphics {
        final List<Fill> fills = new ArrayList<>();
        final List<Text> texts = new ArrayList<>();

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            super.fill(left, top, right, bottom, color);
            fills.add(new Fill(left, top, right, bottom, color));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            super.drawText(text, x, y, color, shadow);
            texts.add(new Text(text, x, y, color, shadow));
        }
    }

    private static class ControllableGame extends DefaultGameBridge {
        boolean spaceDown = false;
        int ticks = 1;

        @Override
        public boolean isKeyDown(String keyName) {
            if ("SPACE".equalsIgnoreCase(keyName)) return spaceDown;
            return false;
        }

        @Override
        public int getClientTicks() {
            return ticks;
        }
    }

    private final RecordingGraphics graphics = new RecordingGraphics();
    private ControllableGame game;

    @BeforeEach
    void setup() {
        before = ConfigManager.toJson();
        ConfigManager.setTestConfigFile(temp.resolve("settings.json").toFile());
        game = new ControllableGame();
        LadsGameBridge.set(game);
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

    @Test
    void classicWidthHeightAtSize50And100And200EqualOldElementValues() {
        KeystrokesHudElement keys = new KeystrokesHudElement();
        keys.setModuleName("Keystrokes");
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        module.style.setIndex(KeystrokesModule.STYLE_CLASSIC);
        module.showCps.set(true);
        module.showSpace.set(true);

        // Classic unscaled bounds are 64x76 regardless of Size slider
        for (int pct : new int[]{50, 100, 200}) {
            module.size.setValue(pct);
            keys.prepareRender(graphics, false);
            assertEquals(64, keys.getWidth(), "Classic width at Size " + pct);
            assertEquals(76, keys.getHeight(), "Classic height at Size " + pct);
            float scale = pct / 100.0f;
            assertEquals((int) Math.ceil(64 * scale), keys.getRenderWidth(), "Classic render width at Size " + pct);
            assertEquals((int) Math.ceil(76 * scale), keys.getRenderHeight(), "Classic render height at Size " + pct);
        }
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
    void cleanSpaceBarRendersFilledBarInsteadOfHyphenGlyph() {
        KeystrokesHudElement keys = new KeystrokesHudElement();
        keys.setModuleName("Keystrokes");
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        module.style.setIndex(KeystrokesModule.STYLE_CLEAN);
        module.showSpace.set(true);

        graphics.fills.clear();
        graphics.texts.clear();
        keys.prepareRender(graphics, false);
        keys.render(graphics);

        // No "-" text glyph drawn for Space bar in Clean mode
        assertTrue(graphics.texts.stream().noneMatch(t -> t.value().equals("-")), "No '-' text glyph drawn");

        // Centered bar (11 px wide x 2 px high) drawn in the space bar row (y + 46)
        boolean foundBar = graphics.fills.stream().anyMatch(f -> (f.right() - f.left() == 11) && (f.bottom() - f.top() == 2));
        assertTrue(foundBar, "Found 11x2 centered spacebar fill");
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
    void anyShadowOrRainbowTweakSwitchesPresetToCustom() {
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        module.style.setIndex(KeystrokesModule.STYLE_CLEAN);

        // Preset 3: Beehive
        module.colorPreset.setIndex(3);
        module.touch();
        assertEquals(3, module.colorPreset.getIndex());

        // Toggling key shadow switches to Custom (9)
        module.keyTextShadow.set(true);
        module.touch();
        assertEquals(9, module.colorPreset.getIndex());

        // Preset 4: Mint & Cream
        module.colorPreset.setIndex(4);
        module.touch();
        assertEquals(4, module.colorPreset.getIndex());

        // Toggling rainbow switches to Custom (9)
        module.rainbowKeyNormal.set(true);
        module.touch();
        assertEquals(9, module.colorPreset.getIndex());
    }

    @Test
    void loadedPresetDoesNotForciblyReapplyOnFirstTouch() {
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");

        // Simulate config load with Preset 4 ("Mint & Cream")
        module.colorPreset.load(new com.google.gson.JsonPrimitive(4));
        // User had customized shadow color on that preset
        module.keyTextShadow.set(true);
        module.touch();

        // Must NOT have reset keyTextShadow to false via forced re-apply of preset 4
        assertTrue(module.keyTextShadow.get());
        assertEquals(9, module.colorPreset.getIndex(), "Tweak recognized as Custom");
    }

    @Test
    void tickSyncedKeyPressesHoldsKeyBetweenFramesUntilTickAdvances() {
        KeystrokesHudElement keys = new KeystrokesHudElement();
        keys.setModuleName("Keystrokes");
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        module.style.setIndex(KeystrokesModule.STYLE_CLEAN);
        module.pressAnimation.set(false); // test raw key registration
        module.tickSyncedKeyPresses.set(true);

        game.ticks = 100;
        game.spaceDown = true;

        // Frame 1: tick 100, space down -> space bar rendered pressed
        keys.prepareRender(graphics, false);
        keys.render(graphics);

        // Game releases key during frame, but tick has not advanced
        game.spaceDown = false;

        // Frame 2: still tick 100 -> key must still be held!
        graphics.fills.clear();
        keys.render(graphics);
        boolean stillPressed = graphics.fills.stream().anyMatch(f -> (f.bottom() - f.top() == 15) && (f.color() == module.keyPressedBackgroundColor.getColor()));
        assertTrue(stillPressed, "Space bar still held pressed within tick 100");

        // Frame 3: tick advances to 101 -> key state sampled as released
        game.ticks = 101;
        graphics.fills.clear();
        keys.render(graphics);
        int idleBg = module.keyBackgroundColor.isUseGlobal() ? HudSettings.getInstance().getGlobalBackground() : module.keyBackgroundColor.getColor();
        boolean released = graphics.fills.stream().anyMatch(f -> (f.bottom() - f.top() == 15) && (f.color() == idleBg));
        assertTrue(released, "Space bar released at tick 101");
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

    @Test
    void cleanColourDefaultsFollowGlobal() {
        KeystrokesHudElement keys = new KeystrokesHudElement();
        keys.setModuleName("Keystrokes");
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        module.style.setIndex(KeystrokesModule.STYLE_CLEAN);

        // Check defaults are useGlobal = true
        assertTrue(module.keyColor.isUseGlobal(), "keyColor should follow global by default");
        assertTrue(module.keyPressedColor.isUseGlobal(), "keyPressedColor should follow global by default");
        assertTrue(module.keyBackgroundColor.isUseGlobal(), "keyBackgroundColor should follow global by default");
        assertTrue(module.keyPressedBackgroundColor.isUseGlobal(), "keyPressedBackgroundColor should follow global by default");
        assertTrue(module.isUseGlobalColor(), "module.isUseGlobalColor() should match keyColor.isUseGlobal()");

        // 1. Changing global color updates clean text color when following global
        int previousGlobal = HudSettings.getInstance().getGlobalColor();
        try {
            HudSettings.getInstance().setGlobalColor(0xFFFF5555);
            graphics.texts.clear();
            keys.prepareRender(graphics, false);
            keys.render(graphics);

            assertTrue(graphics.texts.stream().anyMatch(t -> t.color() == 0xFFFF5555), "Text should be drawn in global color");

            // 2. Setting keyColor useGlobal = false uses custom color
            module.keyColor.setUseGlobal(false);
            module.keyColor.setColor(0xFF00FF00);
            graphics.texts.clear();
            keys.prepareRender(graphics, false);
            keys.render(graphics);

            assertTrue(graphics.texts.stream().anyMatch(t -> t.color() == 0xFF00FF00), "Text should be drawn in custom color");
            assertFalse(module.isUseGlobalColor());
            assertEquals(0xFF00FF00, module.getCustomColor());

            // 3. Background: turning off backgrounds globally hides global plates
            boolean prevBg = HudSettings.getInstance().isBackgrounds();
            try {
                module.keyBackgroundColor.setUseGlobal(true);
                HudSettings.getInstance().setBackgrounds(false);
                graphics.fills.clear();
                keys.prepareRender(graphics, false);
                keys.render(graphics);

                // Idle background should be 0 (no large key background fill with alpha)
                int globalBg = HudSettings.getInstance().getGlobalBackground();
                boolean hasGlobalBgFill = graphics.fills.stream().anyMatch(f -> f.color() == globalBg);
                assertFalse(hasGlobalBgFill, "No key background fill drawn when backgrounds are off globally");

                // Custom background keeps its color even when global backgrounds are off
                module.keyBackgroundColor.setUseGlobal(false);
                module.keyBackgroundColor.setColor(0xAA112233);
                graphics.fills.clear();
                keys.prepareRender(graphics, false);
                keys.render(graphics);
                boolean hasCustomBgFill = graphics.fills.stream().anyMatch(f -> f.color() == 0xAA112233);
                assertTrue(hasCustomBgFill, "Custom background fill drawn even when global backgrounds are off");
            } finally {
                HudSettings.getInstance().setBackgrounds(prevBg);
            }
        } finally {
            HudSettings.getInstance().setGlobalColor(previousGlobal);
        }
    }

    @Test
    void oneTimeMigrationUpgradesExistingCleanToFollowGlobalAndPreservesOwnAfterwards() {
        KeystrokesModule module = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");

        // Old config saved before 1.9.14 (has 1.9.12 keystrokesStyleMigrated flag, but no keystrokesCleanGlobalMigrated)
        // Keystrokes options had useGlobal: false
        String oldJson = """
            {
              "modules": {
                "Keystrokes": {
                  "enabled": true,
                  "options": {
                    "Key Text Color": { "useGlobal": false, "color": -1 },
                    "Pressed Key Text Color": { "useGlobal": false, "color": -16777216 },
                    "Key Background Color": { "useGlobal": false, "color": -1442840576 },
                    "Pressed Key Background Color": { "useGlobal": false, "color": -1426063361 }
                  }
                }
              },
              "hud": {
                "shadowOnDefault": true,
                "hudFpsCapOnDefault": true,
                "hud1910OverrideApplied": true,
                "betterF31911Applied": true,
                "keystrokesStyleMigrated": true
              }
            }
            """;
        JsonObject parsed = JsonParser.parseString(oldJson).getAsJsonObject();
        assertTrue(ConfigManager.needsMigrationSave(parsed), "Old config should need migration save");

        // Applying old config triggers 1.9.14 migration
        ConfigManager.applyJson(parsed);
        assertTrue(module.keyColor.isUseGlobal(), "keyColor should be migrated to follow global");
        assertTrue(module.keyPressedColor.isUseGlobal(), "keyPressedColor should be migrated to follow global");
        assertTrue(module.keyBackgroundColor.isUseGlobal(), "keyBackgroundColor should be migrated to follow global");
        assertTrue(module.keyPressedBackgroundColor.isUseGlobal(), "keyPressedBackgroundColor should be migrated to follow global");

        // Saved config now includes the 1.9.14 flag
        JsonObject saved = ConfigManager.toJson();
        assertTrue(saved.getAsJsonObject("hud").has(ConfigManager.KEYSTROKES_1914_FLAG));
        assertTrue(saved.getAsJsonObject("hud").get(ConfigManager.KEYSTROKES_1914_FLAG).getAsBoolean());

        // Player explicitly changes keyColor to Own
        module.keyColor.setUseGlobal(false);
        module.keyColor.setColor(0xFF445566);
        JsonObject playerSaved = ConfigManager.toJson();

        // Reloading does not overwrite player's explicit choice because the migration already ran
        ConfigManager.applyJson(playerSaved);
        assertFalse(module.keyColor.isUseGlobal(), "Player's choice of Own should be preserved");
        assertEquals(0xFF445566, module.keyColor.getColor());
        assertFalse(ConfigManager.needsMigrationSave(playerSaved));
    }

    @Test
    void matchDockedWidthsLeavesKeystrokesRectWidthUnchangedWhileMatchingOthers() {
        class MockElement extends HudElement {
            MockElement(String name, int px, int py, int w, int h) {
                setPosition(px, py);
                this.width = w;
                this.height = h;
                setModuleName(name);
            }
            @Override public boolean isEnabled() { return true; }
            @Override public void render(LadsGraphics graphics) {}
        }

        KeystrokesModule km = (KeystrokesModule) ModuleManager.getInstance().getModule("Keystrokes");
        boolean prevKmEnabled = km.isEnabled();
        int prevKmStyle = km.style.getIndex();
        km.setEnabled(true);
        km.style.setIndex(KeystrokesModule.STYLE_CLEAN);

        HudSettings settings = HudSettings.getInstance();
        Set<String> previousGroup = settings.getGroupMembers("Keystrokes");
        try {
            settings.addGroup(Set.of("Keystrokes", "WideBox"));

            KeystrokesHudElement keys = new KeystrokesHudElement();
            keys.setModuleName("Keystrokes");
            MockElement wide = new MockElement("WideBox", 10, 120, 140, 20);

            // Keystrokes natural width in Clean mode is 67
            keys.prepareRender(graphics, false);
            assertEquals(67, keys.getWidth());

            Map<HudElement, HudGroupLayout.Rect> bounds = new LinkedHashMap<>();
            bounds.put(keys, new HudGroupLayout.Rect(10, 20, 67, 95));
            bounds.put(wide, new HudGroupLayout.Rect(10, 120, 140, 20));

            HudGroupLayout.matchDockedWidths(bounds);

            // Keystrokes' bounds rect width must remain 67 (NOT widened to 140)
            assertEquals(67, bounds.get(keys).width(), "Keystrokes rect width must remain 67");
            assertEquals(10, bounds.get(keys).x(), "Keystrokes placed at group left edge");
            assertEquals(67, keys.getWidth(), "keys.getWidth() must remain 67");

            // WideBox rect width must be 140
            assertEquals(140, bounds.get(wide).width(), "WideBox width remains widest");

            // Reverse test: Keystrokes grouped with a narrower element (width 40)
            MockElement narrow = new MockElement("WideBox", 10, 120, 40, 20);
            bounds.clear();
            bounds.put(keys, new HudGroupLayout.Rect(10, 20, 67, 95));
            bounds.put(narrow, new HudGroupLayout.Rect(10, 120, 40, 20));

            HudGroupLayout.matchDockedWidths(bounds);

            assertEquals(67, bounds.get(keys).width(), "Keystrokes rect width remains 67");
            assertEquals(67, bounds.get(narrow).width(), "Narrow element matches widest width (67)");
            assertEquals(67, keys.getWidth(), "keys.getWidth() remains 67");

            // Classic style test: Classic unscaled width 64 grouped with wider element
            km.style.setIndex(KeystrokesModule.STYLE_CLASSIC);
            keys.prepareRender(graphics, false);
            assertEquals(64, keys.getWidth());

            bounds.clear();
            bounds.put(keys, new HudGroupLayout.Rect(15, 20, 64, 76));
            bounds.put(wide, new HudGroupLayout.Rect(15, 110, 150, 20));

            HudGroupLayout.matchDockedWidths(bounds);

            assertEquals(64, bounds.get(keys).width(), "Classic Keystrokes rect width remains 64");
            assertEquals(15, bounds.get(keys).x(), "Classic Keystrokes placed at group left edge");
            assertEquals(64, keys.getWidth(), "Classic keys.getWidth() remains 64");
            assertEquals(150, bounds.get(wide).width(), "WideBox width matches widest");
        } finally {
            km.setEnabled(prevKmEnabled);
            km.style.setIndex(prevKmStyle);
            if (previousGroup != null) {
                settings.addGroup(previousGroup);
            } else {
                settings.getGroups().removeIf(g -> g.contains("Keystrokes"));
            }
        }
    }
}
