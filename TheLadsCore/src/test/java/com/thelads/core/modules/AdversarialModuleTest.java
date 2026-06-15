package com.thelads.core.modules;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class AdversarialModuleTest {

    @Test
    public void testBetterF3NullList() {
        BetterF3Module module = new BetterF3Module();
        // If Minecraft accidentally passes null, it should be robust and return null or empty list, 
        // especially since the module is disabled by default!
        // But the worker does `new ArrayList<>(list)` blindly. 
        // This test will crash with NPE to demonstrate the vulnerability.
        module.filterLeftText(null); 
    }

    @Test
    public void testBetterF3EmptyList() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        List<String> result = module.filterLeftText(Collections.emptyList());
        assertTrue(result.isEmpty(), "Empty list should return empty list");
    }

    @Test
    public void testBetterF3UnmodifiableList() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        List<String> input = Collections.singletonList("XYZ: 1 2 3");
        List<String> result = module.filterLeftText(input);
        assertTrue(result.isEmpty(), "Should filter from unmodifiable list");
    }

    @Test
    public void testBetterF3ListWithNullElements() {
        BetterF3Module module = new BetterF3Module();
        module.setEnabled(true);
        List<String> input = java.util.Arrays.asList("XYZ: 1", null, "ABC");
        List<String> result = module.filterLeftText(input);
        assertEquals(2, result.size());
        assertNull(result.get(0));
        assertEquals("ABC", result.get(1));
    }

    @Test
    public void testDynamicFPSLogic() {
        DynamicFPSModule module = new DynamicFPSModule();
        module.setEnabled(true);
        module.setOriginalFramerateLimit(60);
        
        module.onWindowFocusChanged(true);
        assertEquals(60, module.getCurrentFramerateLimit(), "Focused should return original");

        module.onWindowFocusChanged(false);
        assertEquals(15, module.getCurrentFramerateLimit(), "Unfocused should return reduced");

        module.setOriginalFramerateLimit(10);
        assertEquals(10, module.getCurrentFramerateLimit(), "Unfocused should cap at original if lower than 15");

        module.setOriginalFramerateLimit(0); // 0 means vsync or unlimited
        assertEquals(15, module.getCurrentFramerateLimit(), "Unfocused should return 15 if original is 0");
    }
}
