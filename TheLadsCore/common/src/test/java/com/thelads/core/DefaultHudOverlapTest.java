package com.thelads.core;

import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.HudScale;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ensures that in a fresh profile with default HUD settings,
 * no two default-enabled HUD elements overlap at GUI scales 1-4
 * on 1920x1080 and 1280x720 resolutions.
 */
class DefaultHudOverlapTest {

    private static class Screen extends LadsGraphicsTest.MockGraphics {
        int gui;
        @Override public int guiScale() { return gui; }
        Screen at(int w, int h, int scale) { width = w; height = h; gui = scale; return this; }
    }

    private static class Game extends DefaultGameBridge {
        @Override public boolean isIngame() { return true; }
        @Override public boolean hasPlayer() { return true; }
        @Override public int getFps() { return 144; }
        @Override public int getPlayerX() { return 100; }
        @Override public int getPlayerY() { return 64; }
        @Override public int getPlayerZ() { return -200; }
    }

    private final Screen screen = new Screen();
    private JsonObject savedConfig;
    private LadsGameBridge oldBridge;

    @BeforeEach
    void setup() {
        savedConfig = ConfigManager.toJson();
        oldBridge = LadsGameBridge.get();
        LadsGameBridge.set(new Game());
        HudSettings.getInstance().resetToDefaults();
        // Reset all modules to default enabled state
        for (var module : ModuleManager.getInstance().getModules()) {
            module.getOptions().forEach(com.thelads.core.config.Option::reset);
        }
    }

    @AfterEach
    void tearDown() {
        ConfigManager.applyJson(savedConfig);
        LadsGameBridge.set(oldBridge);
    }

    @Test
    void defaultEnabledHudElementsDoNotOverlap() {
        // Resolutions: 1920x1080 (GUI scales 1-4) and 1280x720 (GUI scales 1-3 allowed by Minecraft / HudScale.max)
        int[][] viewports = {
            // 1920x1080 scales 1-4
            { 1920, 1080, 1 },
            { 960, 540, 2 },
            { 640, 360, 3 },
            { 480, 270, 4 },
            // 1280x720 scales 1-3
            { 1280, 720, 1 },
            { 640, 360, 2 },
            { 426, 240, 3 }
        };

        var hud = HudManager.getInstance();
        List<String> allOverlaps = new ArrayList<>();

        for (int[] vp : viewports) {
            int w = vp[0], h = vp[1], scale = vp[2];
            screen.at(w, h, scale);
            hud.render(screen);

            List<String> renderedNames = new ArrayList<>();
            List<Rect> renderedBounds = new ArrayList<>();

            for (var element : hud.getElements()) {
                String name = element.getModuleName();
                if (name == null || !element.isEnabled()) continue;
                Rect b = hud.lastBounds(name);
                if (b != null && b.width() > 0 && b.height() > 0) {
                    renderedNames.add(name);
                    renderedBounds.add(b);
                }
            }

            for (int i = 0; i < renderedNames.size(); i++) {
                for (int j = i + 1; j < renderedNames.size(); j++) {
                    String name1 = renderedNames.get(i);
                    String name2 = renderedNames.get(j);
                    Rect r1 = renderedBounds.get(i);
                    Rect r2 = renderedBounds.get(j);

                    if (r1.intersects(r2)) {
                        allOverlaps.add(String.format("Overlap at %dx%d (scale %d) between '%s' %s and '%s' %s",
                            w, h, scale, name1, r1, name2, r2));
                    }
                }
            }
        }

        assertTrue(allOverlaps.isEmpty(), "Expected zero overlaps in default layout, but found: \n" + String.join("\n", allOverlaps));
    }

    @Test
    void potionEffectsDockedUnderCoordinates() {
        var settings = HudSettings.getInstance();
        int[] pos = settings.getPosition("Potion Effects");
        assertNotNull(pos, "Potion Effects position should be set");
        assertEquals(4, pos[0]);
        assertEquals(160, pos[1]);

        var anchor = settings.getAnchor("Potion Effects");
        assertNotNull(anchor, "Potion Effects should have an anchor");
        assertEquals("Coordinates", anchor.to(), "Potion Effects should be docked to Coordinates");
    }

    @Test
    void hudScaleLimitsMatchMinecraftConstraints() {
        assertEquals(4, HudScale.max(1920, 1080), "1080p allows GUI scales 1-4");
        assertEquals(3, HudScale.max(1280, 720), "720p allows GUI scales 1-3 (720/4 = 180 < 240)");
    }
}
