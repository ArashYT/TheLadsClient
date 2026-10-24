package com.thelads.core;

import com.thelads.core.client.AutoPerspectiveHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AutoPerspectiveTest {
    @BeforeEach
    void setUp() {
        AutoPerspectiveHelper.reset();
    }

    @Test
    void testMountSwitchesToThirdPerson() {
        // Start on foot in first person (0)
        assertEquals(-1, AutoPerspectiveHelper.checkPerspectiveChange(false, 0, true));

        // Mount a horse/boat: should return 1 (third person back)
        assertEquals(1, AutoPerspectiveHelper.checkPerspectiveChange(true, 0, true));

        // Still riding: returns -1 (no change)
        assertEquals(-1, AutoPerspectiveHelper.checkPerspectiveChange(true, 1, true));

        // Dismount: should return 0 (restored original first person)
        assertEquals(0, AutoPerspectiveHelper.checkPerspectiveChange(false, 1, true));
    }

    @Test
    void testDisabledDoesNotChange() {
        assertEquals(-1, AutoPerspectiveHelper.checkPerspectiveChange(true, 0, false));
        assertEquals(-1, AutoPerspectiveHelper.checkPerspectiveChange(false, 0, false));
    }
}
