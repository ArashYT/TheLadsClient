package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EmpiricalChallengerTest {

    @Test
    public void testBetterF3ModuleEdgeCases() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);

        // 1. Null list
        assertNotNull(module.filterLeftText(null), "Null left list should return empty list, not null");
        assertNotNull(module.filterRightText(null), "Null right list should return empty list, not null");

        // 2. List containing null elements
        List<String> listWithNull = Arrays.asList("Valid", null, "XYZ: 123", "Java: 1.8");
        List<String> leftFiltered = module.filterLeftText(listWithNull);
        assertTrue(leftFiltered.contains(null), "Null element should not cause a crash and should be retained");
        assertFalse(leftFiltered.contains("XYZ: 123"), "Left filter should remove XYZ: prefixed strings");
        
        List<String> rightFiltered = module.filterRightText(listWithNull);
        assertTrue(rightFiltered.contains(null), "Null element should not cause a crash and should be retained");
        assertFalse(rightFiltered.contains("Java: 1.8"), "Right filter should remove Java: prefixed strings");

        // 3. Right filter MUST NOT filter XYZ:
        List<String> xyzList = Arrays.asList("XYZ: 123", "Other");
        List<String> rightXyzFiltered = module.filterRightText(xyzList);
        assertTrue(rightXyzFiltered.contains("XYZ: 123"), "Right filter must NOT filter XYZ:");

        // 4. Empty strings
        List<String> emptyList = Arrays.asList("", " ");
        assertDoesNotThrow(() -> module.filterLeftText(emptyList), "Empty strings should not cause crash");
    }

    @Test
    public void testDynamicFPSModuleEdgeCases() {
        DynamicFPSModule module = new DynamicFPSModule();
        module.setEnabled(true);

        // Scenario 1: Focus lost, normal limit
        module.onWindowFocusChanged(false);
        module.setOriginalFramerateLimit(60);
        assertEquals(15, module.getCurrentFramerateLimit(), "When unfocused, 60 FPS should drop to 15");

        // Scenario 2: Focus lost, unlimited (represented by 0)
        module.setOriginalFramerateLimit(0);
        assertEquals(15, module.getCurrentFramerateLimit(), "When unfocused, unlimited (0) should drop to 15");

        // Scenario 3: Focus lost, extremely low limit
        module.setOriginalFramerateLimit(5);
        assertEquals(5, module.getCurrentFramerateLimit(), "When unfocused, 5 FPS should remain 5 (min of 15 and 5)");

        // Scenario 4: Negative limit (Edge case)
        module.setOriginalFramerateLimit(-1);
        int limit = module.getCurrentFramerateLimit();
        // The implementation uses Math.min(15, originalFramerateLimit), which gives -1.
        // It's up to the engine how it handles -1, but let's assert it doesn't crash or return some random high number
        assertTrue(limit <= 15, "Limit should not exceed 15 even with strange negative input");

        // Scenario 5: Focused
        module.onWindowFocusChanged(true);
        module.setOriginalFramerateLimit(120);
        assertEquals(120, module.getCurrentFramerateLimit(), "When focused, framerate should remain original");
    }
}
