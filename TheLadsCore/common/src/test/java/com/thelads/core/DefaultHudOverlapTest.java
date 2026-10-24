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
        /** Xaero's map (on by default) is drawn: the new widgets' tests count its box. */
        boolean minimap;
        @Override public boolean hasMinimap() { return minimap; }
    }

    /** The widgets parity 1.9.19 added (hud-a, hud-b and chat-c), all off by default and placed by HudDefaults. */
    static final String[] NEW_WIDGETS = {"ComboCounter", "PvPInfo", "PotionCounter", "ItemTracker", "Cooldowns", "TPS", "SystemResources",
        "Playtime", "CompassStrip", "ViewAngles", "MouseTrail", "BlockCounter", "Protection", "DeathInfo", "Hearts", "HitIndicator", "InventoryHUD",
        "DamageIndicator", "JumpReset", "MediaPlayer", "HorseStats", "PlayerCounter", "TotemPopCounter", "BlockInfo", "CustomText", "CustomShape",
        "CustomImage", "PlayerHead", "BlockAbove", "EntityCounter", "ShaderPack", "HeldItemLore"};
    static final int[][] VIEWPORTS = {{1920, 1080, 1}, {960, 540, 2}, {640, 360, 3}, {480, 270, 4}, {1280, 720, 1}, {640, 360, 2}, {426, 240, 3}};

    private final Screen screen = new Screen();
    private final Game game = new Game();
    private JsonObject savedConfig;
    private LadsGameBridge oldBridge;

    @BeforeEach
    void setup() {
        savedConfig = ConfigManager.toJson();
        oldBridge = LadsGameBridge.get();
        LadsGameBridge.set(game);
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

    /** Where the default-enabled HUD is drawn on this screen. */
    private java.util.Map<String, Rect> defaults(int w, int h, int scale) {
        game.minimap = true;
        HudSettings.getInstance().setHudFpsCapEnabled(false); // every render lays out again
        var hud = HudManager.getInstance();
        hud.render(screen.at(w, h, scale));
        var placed = new java.util.LinkedHashMap<String, Rect>();
        for (var element : hud.getElements()) {
            Rect b = element.isEnabled() ? hud.lastBounds(element.getModuleName()) : null;
            if (b != null && b.width() > 0 && b.height() > 0) placed.put(element.getModuleName(), b);
        }
        return placed;
    }

    /** A new widget's default box with its editor sample content (its fullest look), as the layout places it. */
    private Rect widget(String name) {
        var element = HudManager.getInstance().get(name);
        assertNotNull(element, name);
        return element.measureBounds(screen, true);
    }

    @Test
    void eachNewWidgetAloneClearsTheDefaultHud() {
        List<String> overlaps = new ArrayList<>();
        for (int[] vp : VIEWPORTS) {
            var placed = defaults(vp[0], vp[1], vp[2]);
            Rect hotbar = com.thelads.core.client.hud.HudAnchors.hotbar(screen);
            Rect group = new Rect(hotbar.x() - 2, hotbar.y() - 52, hotbar.width() + 4, vp[1] - hotbar.y() + 52);
            for (String name : NEW_WIDGETS) {
                assertFalse(ModuleManager.getInstance().getModule(name).isEnabled(), name + " is off by default");
                Rect box = widget(name);
                assertTrue(box.x() >= 0 && box.y() >= 0 && box.right() <= vp[0] && box.bottom() <= vp[1], name + " on screen at " + vp[0] + "x" + vp[1]);
                // Hit Indicator's arcs are drawn round the crosshair, wherever that is: on a 240 px tall GUI the action bar's
                // line (vanilla's place, 46 px under the crosshair) is in reach of an arc for a hit from behind.
                boolean crosshair = name.equals("HitIndicator");
                placed.forEach((other, at) -> {
                    if (box.intersects(at) && !(crosshair && other.equals(com.thelads.core.client.hud.ActionBar.MODULE)))
                        overlaps.add(vp[0] + "x" + vp[1] + " s" + vp[2] + ": " + name + " " + box + " on " + other + " " + at);
                });
                // Vanilla's hotbar, bars and action bar too.
                if (!crosshair && box.intersects(group))
                    overlaps.add(vp[0] + "x" + vp[1] + " s" + vp[2] + ": " + name + " " + box + " on the vanilla hotbar group " + group);
            }
        }
        assertTrue(overlaps.isEmpty(), String.join("\n", overlaps));
    }

    /**
     * On GUIs of 960x540 and more (1080p at GUI scale 1-2, 720p at 1) every new widget fits at once: none overlaps another, the
     * default HUD, vanilla's hotbar group (hotbar, bars, action bar), closed chat or a full scoreboard sidebar. Smaller GUIs have less
     * room than the widgets need together, so there each one clears the default HUD on its own (above).
     */
    @Test
    void allNewWidgetsTogetherFitOnLargeGuis() {
        List<String> overlaps = new ArrayList<>();
        for (int[] vp : VIEWPORTS) {
            int w = vp[0], h = vp[1];
            if (w < 960 || h < 540) continue;
            var placed = defaults(w, h, vp[2]);
            Rect hotbar = com.thelads.core.client.hud.HudAnchors.hotbar(screen);
            placed.put("vanilla hotbar group", new Rect(hotbar.x() - 2, hotbar.y() - 52, hotbar.width() + 4, h - hotbar.y() + 52));
            placed.put("vanilla chat", new Rect(0, h - 48 - 90, 322, 98));
            placed.put("scoreboard sidebar", new Rect(w - 124, h / 2 - 80, 124, 160));
            var boxes = new java.util.LinkedHashMap<String, Rect>();
            for (String name : NEW_WIDGETS) boxes.put(name, widget(name));
            for (var a : boxes.entrySet()) {
                for (var b : boxes.entrySet())
                    if (a.getKey().compareTo(b.getKey()) < 0 && a.getValue().intersects(b.getValue()))
                        overlaps.add(w + "x" + h + ": " + a.getKey() + " " + a.getValue() + " on " + b.getKey() + " " + b.getValue());
                placed.forEach((other, at) -> {
                    if (a.getValue().intersects(at)) overlaps.add(w + "x" + h + ": " + a.getKey() + " " + a.getValue() + " on " + other + " " + at);
                });
            }
        }
        assertTrue(overlaps.isEmpty(), String.join("\n", overlaps));
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
