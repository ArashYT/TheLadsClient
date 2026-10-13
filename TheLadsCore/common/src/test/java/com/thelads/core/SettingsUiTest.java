package com.thelads.core;

import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import com.thelads.core.client.hypixel.HypixelMods;
import com.thelads.core.modules.OldAnimationsModule;

import static org.junit.jupiter.api.Assertions.*;

class SettingsUiTest {
    @TempDir Path temporary;
    private ModsMenuTest.OwnershipFixture ownership;

    @BeforeEach void setUp() throws Exception {
        ConfigManager.setTestConfigFile(temporary.resolve("config.json").toFile());
        ownership = new ModsMenuTest.OwnershipFixture();
        ModuleSupport.registerBuiltIn("Coordinates", HypixelMods.HYPIXEL_MODS, "Fullbright", OldAnimationsModule.NAME, "Zoom");
    }

    @AfterEach void tearDown() throws Exception {
        if (ownership != null) ownership.close();
    }

    @Test void testHasPreviewDecisions() {
        Module coords = ModuleManager.getInstance().getModule("Coordinates");
        assertNotNull(coords);
        assertTrue(LadsSettingsScreen.hasPreview(coords), "HUD modules must have preview");

        Module hypixel = ModuleManager.getInstance().getModule(HypixelMods.HYPIXEL_MODS);
        assertNotNull(hypixel);
        assertFalse(LadsSettingsScreen.hasPreview(hypixel), "Hypixel Mods must NOT have preview (placeholder only)");

        Module fullbright = ModuleManager.getInstance().getModule("Fullbright");
        assertNotNull(fullbright);
        assertTrue(LadsSettingsScreen.hasPreview(fullbright), "Fullbright has live game view preview");

        Module oldAnim = ModuleManager.getInstance().getModule(OldAnimationsModule.NAME);
        assertNotNull(oldAnim);
        assertFalse(LadsSettingsScreen.hasPreview(oldAnim), "OldAnimations placeholder card has no real preview");
    }

    @Test void testTwoColumnLayoutWhenNoPreview() {
        var menu = new LadsSettingsScreen();
        menu.openModule("Hypixel Mods"); // tests case-insensitive openModule
        assertFalse(menu.currentDetailHasPreview());

        var g = new ModsMenuTest.Graphics();
        g.width = 640;
        g.height = 360;
        g.render(menu);

        // Preview title "PREVIEW" should NOT be rendered
        assertNull(g.buttons.get("PREVIEW"), "Preview panel must not be rendered for Hypixel Mods");

        // Controls should be in two columns across the full width
        var first = menu.controlBounds("option:" + HypixelMods.AUTO_GG);
        var second = menu.controlBounds("option:" + HypixelMods.AUTO_GG + ": Message");
        assertNotNull(first, "First option control must exist");
        assertNotNull(second, "Second option control must exist");

        // First option is in column 0 (left), second option is in column 1 (right)
        // With 640px width and side panel (118px): x is ~134, w is ~486.
        // Left column is near x (~134), right column is near x + colW + gap (> 300).
        int firstX = first.x();
        int secondX = second.x();
        assertNotEquals(firstX, secondX, "Options should be in different columns");
        assertTrue(secondX > firstX + 150, "Second option should be in right column");
    }

    @Test void testPreviewLayoutWhenHasPreview() {
        var menu = new LadsSettingsScreen();
        menu.openModule("Coordinates");
        assertTrue(menu.currentDetailHasPreview());

        var g = new ModsMenuTest.Graphics();
        g.width = 640;
        g.height = 360;
        g.render(menu);

        // Live HUD preview title should be rendered
        assertTrue(g.drawCalls.stream().anyMatch(call -> call.contains("Live HUD Preview") || call.contains("PREVIEW")),
            "Preview panel should be rendered for HUD element");
    }

    @Test void testScrollbarDragAndClickAndArrowKeys() {
        var menu = new LadsSettingsScreen();
        menu.openModule("Coordinates");

        var g = new ModsMenuTest.Graphics();
        g.width = 640;
        g.height = 200; // Small height to ensure maxScroll > 0
        g.render(menu);

        int maxScroll = menu.getMaxScroll();
        assertTrue(maxScroll > 0, "Page should be scrollable with small height");
        assertEquals(0, menu.getScrollOffset());

        // Test Down Arrow key (264)
        boolean downUsed = menu.keyPressed(264, 0);
        assertTrue(downUsed, "Down arrow should be consumed");
        assertEquals(28, menu.getScrollOffset(), "Down arrow should scroll down by 28px");

        // Test Up Arrow key (265)
        boolean upUsed = menu.keyPressed(265, 0);
        assertTrue(upUsed, "Up arrow should be consumed");
        assertEquals(0, menu.getScrollOffset(), "Up arrow should scroll up by 28px back to 0");

        // Test Page Down / Click track below thumb
        var track = menu.scrollbarTrackBounds();
        var thumb = menu.scrollbarThumbBounds();
        assertNotNull(track);
        assertNotNull(thumb);

        // Click track below thumb (e.g. at track.y + track.height - 2)
        boolean trackClicked = menu.mouseClicked(track.x() + 2, track.y() + track.height() - 2, 0);
        assertTrue(trackClicked, "Track click should be handled");
        assertTrue(menu.getScrollOffset() > 0, "Track click below thumb should page down");

        // Click track above thumb (at track.y + 1)
        int scrolledOffset = menu.getScrollOffset();
        boolean trackAboveClicked = menu.mouseClicked(track.x() + 2, track.y() + 1, 0);
        assertTrue(trackAboveClicked);
        assertTrue(menu.getScrollOffset() < scrolledOffset, "Track click above thumb should page up");

        // Test thumb drag
        g.render(menu); // re-render to update thumb pos
        thumb = menu.scrollbarThumbBounds();
        assertNotNull(thumb);

        // Click on thumb
        assertTrue(menu.mouseClicked(thumb.x() + 2, thumb.y() + thumb.height() / 2.0, 0));
        assertTrue(menu.isDraggingScrollbar());

        // Drag down
        assertTrue(menu.mouseDragged(thumb.x() + 2, thumb.y() + 50, 0, 0, 50));
        assertTrue(menu.getScrollOffset() > 0, "Dragging thumb down should increase scroll offset");

        // Release
        assertTrue(menu.mouseReleased(thumb.x() + 2, thumb.y() + 50, 0));
        assertFalse(menu.isDraggingScrollbar());
    }

    @Test void testScrollIntoViewAndTabNavigation() {
        var menu = new LadsSettingsScreen();
        menu.openModule("Coordinates");

        var g = new ModsMenuTest.Graphics();
        g.width = 640;
        g.height = 200;
        g.render(menu);

        int maxScroll = menu.getMaxScroll();
        assertTrue(maxScroll > 0);

        // Test explicit scrollIntoView with a rect below the viewport
        var track = menu.scrollbarTrackBounds();
        assertNotNull(track);
        var belowRect = new LadsSettingsScreen.Rect(track.x(), track.y() + track.height() + 50, 50, 20);
        menu.scrollIntoView(belowRect);
        assertTrue(menu.getScrollOffset() > 0, "scrollIntoView for rect below viewport should scroll down");

        // Test explicit scrollIntoView with a rect above the viewport
        var aboveRect = new LadsSettingsScreen.Rect(track.x(), track.y() - 30, 50, 20);
        int prevOffset = menu.getScrollOffset();
        menu.scrollIntoView(aboveRect);
        assertTrue(menu.getScrollOffset() < prevOffset, "scrollIntoView for rect above viewport should scroll up");

        // Test Tab key (258) navigation
        menu.scrollBy(-10000); // reset to top
        assertEquals(0, menu.getScrollOffset());
        assertTrue(menu.keyPressed(258, 0), "Tab key should be consumed");
        assertFalse(menu.getFocusId().isEmpty(), "Tab key should assign focus");
    }
}
