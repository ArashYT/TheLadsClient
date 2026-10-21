package com.thelads.core;

import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.hud.CoordinatesHudElement;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.9.14: Unit tests for Coordinates HUD "C" counter option, placement, and zero-allocation text caching.
 */
class CoordinatesHudCTest {

    private static final class TestGame extends DefaultGameBridge {
        int renderedChunks = 142;
        boolean hasPlayer = true;
        int posX = 23, posY = 66, posZ = 34;
        float yaw = 45f; // SW

        @Override public boolean hasPlayer() { return hasPlayer; }
        @Override public int getPlayerX() { return posX; }
        @Override public int getPlayerY() { return posY; }
        @Override public int getPlayerZ() { return posZ; }
        @Override public float getYaw() { return yaw; }
        @Override public String getPlayerDirection() { return "South-West"; }
        @Override public int getRenderedChunks() { return renderedChunks; }
    }

    private static final class TestGraphics extends LadsGraphicsTest.MockGraphics {
        record DrawnText(String text, int x, int y, int color, boolean shadow) {}
        final List<DrawnText> drawnTexts = new ArrayList<>();
        int widthCalls = 0;

        TestGraphics() { width = 640; height = 360; }

        @Override
        public int textWidth(String text) {
            widthCalls++;
            return text != null ? text.length() * 6 : 0;
        }

        @Override
        public int fontHeight() {
            return 9;
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            drawnTexts.add(new DrawnText(text, x, y, color, shadow));
            super.drawText(text, x, y, color, shadow);
        }

        void clear() {
            drawnTexts.clear();
            drawCalls.clear();
            widthCalls = 0;
        }
    }

    private JsonObject savedConfig;
    private LadsGameBridge oldBridge;
    private final TestGame game = new TestGame();
    private final TestGraphics graphics = new TestGraphics();
    private CoordinatesHudElement coords;

    @BeforeEach
    void setup() {
        savedConfig = ConfigManager.toJson();
        oldBridge = LadsGameBridge.get();
        LadsGameBridge.set(game);

        var module = ModuleManager.getInstance().getModule("Coordinates");
        module.getOptions().forEach(com.thelads.core.config.Option::reset);
        module.setEnabled(true);

        coords = new CoordinatesHudElement();
        coords.setModuleName("Coordinates");
    }

    @AfterEach
    void tearDown() {
        ConfigManager.applyJson(savedConfig);
        LadsGameBridge.set(oldBridge);
    }

    private static void setBool(String optionName, boolean value) {
        var opt = ModuleManager.getInstance().getModule("Coordinates").getOption(optionName);
        assertNotNull(opt, "Option must exist: " + optionName);
        ((BoolOption) opt).set(value);
    }

    @Test
    void showCOptionDefaultsToOff() {
        var opt = ModuleManager.getInstance().getModule("Coordinates").getOption("Show C");
        assertNotNull(opt, "Show C option must be registered in ModuleManager");
        assertInstanceOf(BoolOption.class, opt);
        assertFalse(((BoolOption) opt).get(), "Show C must default to OFF");
    }

    @Test
    void whenShowCIsOffNoCTextIsRendered() {
        setBool("Show C", false);
        coords.render(graphics);

        boolean hasC = graphics.drawnTexts.stream().anyMatch(dt -> dt.text().startsWith("C:"));
        assertFalse(hasC, "When Show C is false, no C text should be rendered");
    }

    @Test
    void whenShowCIsTrueRendersOnRowThreeRightColumnInVerticalMode() {
        setBool("Show C", true);
        setBool("Vertical", true);
        game.renderedChunks = 142;

        coords.prepareRender(graphics, false);
        coords.render(graphics);

        // Verify "C: 142" appears in drawn texts
        var cEntry = graphics.drawnTexts.stream()
                .filter(dt -> dt.text().equals("C: 142"))
                .findFirst()
                .orElse(null);
        assertNotNull(cEntry, "C: 142 must be rendered when Show C is enabled");

        // Row 3: level with Z (y + 3 + 2 * lineHeight)
        int lineHeight = graphics.fontHeight() + 2; // 11
        int expectedY = coords.getY() + 3 + 2 * lineHeight;
        assertEquals(expectedY, cEntry.y(), "C counter must render on row 3 (level with Z row)");

        // Right column alignment: x + width - 4 - wC
        int expectedX = coords.getX() + coords.getWidth() - 4 - graphics.textWidth("C: 142");
        assertEquals(expectedX, cEntry.x(), "C counter must be right-aligned in the right column");

        // Direction elements SW and -X +Z should be directly above it on rows 1 and 2
        var swEntry = graphics.drawnTexts.stream().filter(dt -> dt.text().equals("SW")).findFirst().orElseThrow();
        var axesEntry = graphics.drawnTexts.stream().filter(dt -> dt.text().equals("-X +Z")).findFirst().orElseThrow();
        assertEquals(coords.getY() + 3, swEntry.y(), "Compass on row 1");
        assertEquals(coords.getY() + 3 + lineHeight, axesEntry.y(), "Axes on row 2");

        // Same color and shadow rules as direction column
        assertEquals(swEntry.color(), cEntry.color(), "C counter must share base color with direction column");
        assertEquals(swEntry.shadow(), cEntry.shadow(), "C counter must share shadow setting with direction column");
    }

    @Test
    void cTextCachingReusesSameStringInstanceWhenChunkCountUnchanged() {
        setBool("Show C", true);
        setBool("Vertical", true);
        game.renderedChunks = 256;

        coords.prepareRender(graphics, false);
        String firstInstance = coords.getCachedCText();
        assertEquals("C: 256", firstInstance);

        // Next frame with same chunk count
        coords.prepareRender(graphics, false);
        String secondInstance = coords.getCachedCText();
        assertSame(firstInstance, secondInstance, "Cached C text string must be reused across frames when chunk count is identical");

        // When chunk count changes, new String is cached
        game.renderedChunks = 257;
        coords.prepareRender(graphics, false);
        String thirdInstance = coords.getCachedCText();
        assertEquals("C: 257", thirdInstance);
        assertNotSame(firstInstance, thirdInstance, "New string instance must be created when chunk count changes");
    }

    @Test
    void prepareRenderSkipsMeasurementWhenStateUnchanged() {
        setBool("Show C", true);
        setBool("Vertical", true);
        game.renderedChunks = 100;

        coords.prepareRender(graphics, false);
        int initialWidthCalls = graphics.widthCalls;
        assertTrue(initialWidthCalls > 0);

        // Calling prepareRender with identical state
        graphics.clear();
        coords.prepareRender(graphics, false);
        assertEquals(0, graphics.widthCalls, "No text metrics measuring should occur when state has not changed");

        // Changing rendered chunks triggers re-measurement
        game.renderedChunks = 101;
        coords.prepareRender(graphics, false);
        assertTrue(graphics.widthCalls > 0, "Re-measurement must occur when chunk count changes");
    }

    @Test
    void horizontalLayoutAppendsCWhenEnabled() {
        setBool("Show C", true);
        setBool("Vertical", false);
        game.renderedChunks = 99;

        coords.prepareRender(graphics, false);
        coords.render(graphics);

        boolean found = graphics.drawnTexts.stream().anyMatch(dt -> dt.text().contains("C: 99"));
        assertTrue(found, "Horizontal layout must include C counter when Show C is enabled");
    }

    @Test
    void noPlayerResetsStateGracefully() {
        setBool("Show C", true);
        game.hasPlayer = false;

        coords.prepareRender(graphics, false);
        coords.render(graphics);

        assertNull(coords.getCText());
        assertTrue(graphics.drawnTexts.isEmpty(), "No text should be drawn when player is absent");
    }
}
