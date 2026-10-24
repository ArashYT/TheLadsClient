package com.thelads.core.client.ctm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CtmRulesTest {
    @Test
    void testParseRule() {
        String props = "matchBlocks=glass stained_glass\n" +
                "method=ctm\n" +
                "tiles=0-46\n" +
                "connect=block\n" +
                "faces=all\n";

        CtmRules.Rule rule = CtmRules.parseRule("optifine/ctm/glass.properties", props);
        assertNotNull(rule);
        assertEquals(CtmRules.Method.CTM, rule.method);
        assertTrue(rule.matchesBlock("glass"));
        assertTrue(rule.matchesBlock("stained_glass"));
        assertFalse(rule.matchesBlock("stone"));
        assertTrue(rule.matchesFace("north"));
        assertEquals(47, rule.tiles.length);
        assertEquals("0", rule.tiles[0]);
        assertEquals("46", rule.tiles[46]);
    }

    @Test
    void testCtmIndexCalculation() {
        // Isolated (no neighbors connected) -> 0
        assertEquals(0, CtmRules.calculateCtmIndex(false, false, false, false, false, false, false, false));

        // Full connection (all 8 neighbors connected) -> 26
        assertEquals(26, CtmRules.calculateCtmIndex(true, true, true, true, true, true, true, true));

        // Vertical strip (up and down, no left/right) -> 9
        assertEquals(9, CtmRules.calculateCtmIndex(true, true, false, false, false, false, false, false));

        // Horizontal strip (left and right, no up/down) -> 10
        assertEquals(10, CtmRules.calculateCtmIndex(false, false, true, true, false, false, false, false));
    }

    @Test
    void testHorizontalVerticalIndices() {
        assertEquals(0, CtmRules.calculateHorizontalIndex(false, false));
        assertEquals(2, CtmRules.calculateHorizontalIndex(true, true));
        assertEquals(3, CtmRules.calculateHorizontalIndex(true, false));
        assertEquals(1, CtmRules.calculateHorizontalIndex(false, true));

        assertEquals(0, CtmRules.calculateVerticalIndex(false, false));
        assertEquals(2, CtmRules.calculateVerticalIndex(true, true));
        assertEquals(3, CtmRules.calculateVerticalIndex(true, false));
        assertEquals(1, CtmRules.calculateVerticalIndex(false, true));
    }

    @Test
    void testRepeatIndex() {
        assertEquals(0, CtmRules.calculateRepeatIndex(0, 0, 2, 2));
        assertEquals(1, CtmRules.calculateRepeatIndex(1, 0, 2, 2));
        assertEquals(2, CtmRules.calculateRepeatIndex(0, 1, 2, 2));
        assertEquals(3, CtmRules.calculateRepeatIndex(1, 1, 2, 2));
    }
}
