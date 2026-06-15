package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class BetterF3ModuleStressTest {

    @Test
    public void testNullList() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        assertDoesNotThrow(() -> {
            List<String> result = module.filterLeftText(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }, "Should handle null list gracefully without NullPointerException");
    }

    @Test
    public void testEmptyList() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        assertDoesNotThrow(() -> {
            List<String> result = module.filterLeftText(Collections.emptyList());
            assertNotNull(result);
            assertTrue(result.isEmpty());
        });
    }

    @Test
    public void testUnmodifiableList() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        List<String> list = Collections.unmodifiableList(List.of("XYZ: 123", "Other: 456"));
        assertDoesNotThrow(() -> {
            List<String> result = module.filterLeftText(list);
            assertEquals(1, result.size());
            assertEquals("Other: 456", result.get(0));
        });
    }

    @Test
    public void testListWithNullElements() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        List<String> list = new ArrayList<>();
        list.add(null);
        list.add("XYZ: 123");
        assertDoesNotThrow(() -> {
            List<String> result = module.filterLeftText(list);
            assertEquals(1, result.size());
            assertNull(result.get(0));
        });
    }
}
