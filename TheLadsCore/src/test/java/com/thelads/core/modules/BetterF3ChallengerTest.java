package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BetterF3ChallengerTest {

    @Test
    public void testFilterRightTextDoesNotRemoveXYZ() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        List<String> list = Arrays.asList("XYZ: 123", "Java: 1.8.0");
        List<String> result = module.filterRightText(list);
        
        assertEquals(1, result.size(), "Should only remove Java:");
        assertEquals("XYZ: 123", result.get(0), "XYZ: should not be removed from the right text");
    }

    @Test
    public void testFilterLeftTextRemovesXYZ() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        List<String> list = Arrays.asList("XYZ: 123", "Java: 1.8.0");
        List<String> result = module.filterLeftText(list);
        
        assertEquals(1, result.size(), "Should only remove XYZ:");
        assertEquals("Java: 1.8.0", result.get(0), "Java: should not be removed from the left text");
    }

    @Test
    public void testDisabledModuleDoesNotFilter() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(false);
        List<String> list = Arrays.asList("XYZ: 123", "Java: 1.8.0");
        
        List<String> leftResult = module.filterLeftText(list);
        assertEquals(2, leftResult.size(), "Disabled module should not remove left text");
        
        List<String> rightResult = module.filterRightText(list);
        assertEquals(2, rightResult.size(), "Disabled module should not remove right text");
    }

    @Test
    public void testNullInputReturnsEmptyMutableList() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        
        List<String> leftResult = module.filterLeftText(null);
        assertNotNull(leftResult);
        assertTrue(leftResult.isEmpty());
        // verify it's mutable
        leftResult.add("Test");
        assertEquals(1, leftResult.size());

        List<String> rightResult = module.filterRightText(null);
        assertNotNull(rightResult);
        assertTrue(rightResult.isEmpty());
        rightResult.add("Test");
        assertEquals(1, rightResult.size());
    }

    @Test
    public void testConcurrentModificationLikeInput() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        List<String> list = Collections.unmodifiableList(Arrays.asList("XYZ: 1", "Java: 2"));
        
        assertDoesNotThrow(() -> {
            module.filterLeftText(list);
            module.filterRightText(list);
        }, "Should copy into mutable list to prevent UnsupportedOperationException");
    }
}
