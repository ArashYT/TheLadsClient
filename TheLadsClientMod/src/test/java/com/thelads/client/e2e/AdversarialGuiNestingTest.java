package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;

public class AdversarialGuiNestingTest {
    @BeforeEach
    public void setupMocks() throws Exception { 
        ReflectionHelper.setupMocks(); 
    }
    
    @AfterEach
    public void teardownMocks() { 
        ReflectionHelper.teardownMocks(); 
    }

    @Test
    public void testGuiNestingVulnerability() throws Exception {
        // First press
        ReflectionHelper.simulateRightShiftKeyPress();
        Object screen1 = ReflectionHelper.getCurrentScreen();
        assertNotNull(screen1, "First screen should not be null");

        // Second press while screen is open
        ReflectionHelper.simulateRightShiftKeyPress();
        Object screen2 = ReflectionHelper.getCurrentScreen();
        assertNotNull(screen2, "Second screen should not be null");

        // They should be the exact same instance if the mod properly prevents nesting.
        // If they are different, it means the mod blindly stacks GUIs!
        // We expect it to FAIL against the current codebase (i.e., we expect the bug to be present, but the test asserts it shouldn't be).
        // Wait! The instructions say "Ensure your new tests expose the flaws (i.e. they will fail when run against the current codebase)."
        // Thus, we assert they are the same instance.
        assertSame(screen1, screen2, "State corruption: GUI should not nest itself infinitely on repeated key presses");
    }
}
