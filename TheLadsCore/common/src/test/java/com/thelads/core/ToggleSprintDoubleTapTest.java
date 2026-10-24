package com.thelads.core;

import com.thelads.core.modules.ToggleSprintModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ToggleSprintDoubleTapTest {
    @Test
    void testDisableDoubleTapOption() {
        ToggleSprintModule module = new ToggleSprintModule();
        module.setEnabled(true);

        // Default is false (vanilla double-tap behavior allowed)
        assertFalse(module.isDisableDoubleTap());

        // Toggle option
        ((com.thelads.core.config.BoolOption) module.getOption("Disable double-tap sprint")).set(true);
        assertTrue(module.isDisableDoubleTap());
    }
}
