package com.thelads.core.client.hypixel;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerminalSolverTest {

    @BeforeEach
    void setUp() {
        Module m = ModuleManager.getInstance().getModule("SkyBlock Suite");
        if (m != null) {
            m.setEnabled(true);
            BoolOption opt = (BoolOption) m.getOption("Terminal Solvers");
            if (opt != null) opt.set(true);
        }
        HypixelSafetyManager.bypass("SkyBlock Suite");
    }

    @AfterEach
    void tearDown() {
        ModuleManager.getInstance().resetToDefaults();
    }

    @Test
    void testDetectTerminalType() {
        assertEquals(TerminalSolver.TerminalType.CLICK_IN_ORDER, TerminalSolver.detectType("Click in order!"));
        assertEquals(TerminalSolver.TerminalType.CLICK_IN_ORDER, TerminalSolver.detectType("Click in order from 1-14"));
        assertEquals(TerminalSolver.TerminalType.CLICK_IN_ORDER, TerminalSolver.detectType("Numbers"));

        assertEquals(TerminalSolver.TerminalType.SELECT_COLOR, TerminalSolver.detectType("Select all the Red items!"));
        assertEquals(TerminalSolver.TerminalType.SELECT_COLOR, TerminalSolver.detectType("Select all the Blue items!"));
        assertEquals(TerminalSolver.TerminalType.SELECT_COLOR, TerminalSolver.detectType("Select all the Green items!"));

        assertEquals(TerminalSolver.TerminalType.STARTS_WITH_LETTER, TerminalSolver.detectType("What starts with: 'B'?"));
        assertEquals(TerminalSolver.TerminalType.STARTS_WITH_LETTER, TerminalSolver.detectType("What starts with: 'S'?"));

        assertEquals(TerminalSolver.TerminalType.CORRECT_PANES, TerminalSolver.detectType("Correct all the panes!"));

        assertEquals(TerminalSolver.TerminalType.NONE, TerminalSolver.detectType("Chest"));
        assertEquals(TerminalSolver.TerminalType.NONE, TerminalSolver.detectType(""));
        assertEquals(TerminalSolver.TerminalType.NONE, TerminalSolver.detectType(null));
    }

    @Test
    void testExtractTargetColor() {
        assertEquals("red", TerminalSolver.extractTargetColor("Select all the Red items!"));
        assertEquals("blue", TerminalSolver.extractTargetColor("Select all the Blue items!"));
        assertEquals("yellow", TerminalSolver.extractTargetColor("Select all the Yellow items!"));
        assertEquals("green", TerminalSolver.extractTargetColor("Select all the Green items!"));
        assertEquals("light blue", TerminalSolver.extractTargetColor("Select all the Light Blue items!"));
    }

    @Test
    void testExtractTargetLetter() {
        assertEquals('B', TerminalSolver.extractTargetLetter("What starts with: 'B'?"));
        assertEquals('S', TerminalSolver.extractTargetLetter("What starts with: 'S'?"));
        assertEquals('M', TerminalSolver.extractTargetLetter("Click all items that start with: 'M'"));
        assertEquals('C', TerminalSolver.extractTargetLetter("What starts with: C"));
    }

    @Test
    void testIsSlotCompleted() {
        assertTrue(TerminalSolver.isSlotCompleted(null, 0));
        assertTrue(TerminalSolver.isSlotCompleted("", 0));
        assertTrue(TerminalSolver.isSlotCompleted("Item", 0));
        assertTrue(TerminalSolver.isSlotCompleted("Lime Stained Glass Pane", 1));
        assertTrue(TerminalSolver.isSlotCompleted("Green Stained Glass Pane", 1));
        assertFalse(TerminalSolver.isSlotCompleted("Red Stained Glass Pane", 1));
        assertFalse(TerminalSolver.isSlotCompleted("Bow", 1));
        assertFalse(TerminalSolver.isSlotCompleted("Lime Wool", 1));
        assertFalse(TerminalSolver.isSlotCompleted("Lime Dye", 1));
        assertFalse(TerminalSolver.isSlotCompleted("Lime Carpet", 1));
        assertFalse(TerminalSolver.isSlotCompleted("Lime Stained Glass Pane", 1, true));
        assertFalse(TerminalSolver.isSlotCompleted("Green Stained Glass Pane", 1, true));
    }

    @Test
    void testSolveClickInOrder() {
        String title = "Click in order!";
        int totalSlots = 54; // 18 chest slots + 36 player slots

        java.util.List<TerminalSolver.SlotInfo> slots = java.util.List.of(
            new TerminalSolver.SlotInfo(0, 1, "Red Stained Glass Pane"),
            new TerminalSolver.SlotInfo(1, 2, "Red Stained Glass Pane"),
            new TerminalSolver.SlotInfo(2, 3, "Red Stained Glass Pane"),
            new TerminalSolver.SlotInfo(3, 4, "Red Stained Glass Pane"),
            new TerminalSolver.SlotInfo(4, 5, "Red Stained Glass Pane")
        );

        int target = TerminalSolver.findClickInOrderTarget(slots, totalSlots);
        assertEquals(1, target, "Initial target should be 1");

        // Target number 1 is highlighted
        int color1 = TerminalSolver.getSlotHighlight(title, 0, 1, "Red Stained Glass Pane", totalSlots, target);
        assertEquals(TerminalSolver.COLOR_TARGET, color1);

        // Target number 5 is NOT highlighted when currentTarget == 1
        int color5 = TerminalSolver.getSlotHighlight(title, 4, 5, "Red Stained Glass Pane", totalSlots, target);
        assertEquals(0, color5);

        // When target is 5 (numbers 1-4 are completed), slot with stack 5 is highlighted
        int color5Active = TerminalSolver.getSlotHighlight(title, 4, 5, "Red Stained Glass Pane", totalSlots, 5);
        assertEquals(TerminalSolver.COLOR_TARGET, color5Active);

        // Completed slot is NOT highlighted
        int colorCompleted = TerminalSolver.getSlotHighlight(title, 2, 1, "Lime Stained Glass Pane", totalSlots, target);
        assertEquals(0, colorCompleted);

        // Player inventory slots are NOT highlighted
        int playerSlot = TerminalSolver.getSlotHighlight(title, 40, 1, "Red Stained Glass Pane", totalSlots, target);
        assertEquals(0, playerSlot);
    }

    @Test
    void testSolveSelectColor() {
        String title = "Select all the Red items!";
        int totalSlots = 54;

        // Red items highlighted
        int red = TerminalSolver.getSlotHighlight(title, 2, 1, "Red Wool", totalSlots);
        assertEquals(TerminalSolver.COLOR_TARGET, red);

        int redDye = TerminalSolver.getSlotHighlight(title, 3, 1, "§cRed Dye", totalSlots);
        assertEquals(TerminalSolver.COLOR_TARGET, redDye);

        // Blue items NOT highlighted
        int blue = TerminalSolver.getSlotHighlight(title, 4, 1, "Blue Wool", totalSlots);
        assertEquals(0, blue);

        // Lime/green completed items NOT highlighted
        int lime = TerminalSolver.getSlotHighlight(title, 5, 1, "Lime Stained Glass Pane", totalSlots);
        assertEquals(0, lime);
    }

    @Test
    void testSolveSelectLimeItems() {
        String title = "Select all the Lime items!";
        int totalSlots = 54;

        // Lime Wool and Lime Dye MUST be highlighted
        int limeWool = TerminalSolver.getSlotHighlight(title, 2, 1, "Lime Wool", totalSlots);
        assertEquals(TerminalSolver.COLOR_TARGET, limeWool);

        int limeDye = TerminalSolver.getSlotHighlight(title, 3, 1, "§aLime Dye", totalSlots);
        assertEquals(TerminalSolver.COLOR_TARGET, limeDye);

        // Red Wool should not be highlighted
        int red = TerminalSolver.getSlotHighlight(title, 4, 1, "Red Wool", totalSlots);
        assertEquals(0, red);
    }

    @Test
    void testSolveStartsWithLetterL() {
        String title = "What starts with: 'L'!";
        int totalSlots = 54;

        int limeDye = TerminalSolver.getSlotHighlight(title, 0, 1, "Lime Dye", totalSlots);
        assertEquals(TerminalSolver.COLOR_TARGET, limeDye);
    }

    @Test
    void testSolveStartsWithLetter() {
        String title = "What starts with: 'B'?" ;
        int totalSlots = 54;

        // Bow starts with B
        int bow = TerminalSolver.getSlotHighlight(title, 0, 1, "Bow", totalSlots);
        assertEquals(TerminalSolver.COLOR_TARGET, bow);

        // Bone with color formatting starts with B
        int bone = TerminalSolver.getSlotHighlight(title, 1, 1, "§aBone", totalSlots);
        assertEquals(TerminalSolver.COLOR_TARGET, bone);

        // Arrow starts with A -> NOT highlighted
        int arrow = TerminalSolver.getSlotHighlight(title, 2, 1, "Arrow", totalSlots);
        assertEquals(0, arrow);
    }

    @Test
    void testGatingDisablesHighlighting() {
        String title = "Click in order!";
        int totalSlots = 54;

        // Turn off module
        Module m = ModuleManager.getInstance().getModule("SkyBlock Suite");
        m.setEnabled(false);
        assertEquals(0, TerminalSolver.getSlotHighlight(title, 5, 1, "Red Stained Glass Pane", totalSlots));

        // Turn on module but disable Terminal Solvers option
        m.setEnabled(true);
        BoolOption opt = (BoolOption) m.getOption("Terminal Solvers");
        opt.set(false);
        assertEquals(0, TerminalSolver.getSlotHighlight(title, 5, 1, "Red Stained Glass Pane", totalSlots));
    }
}
