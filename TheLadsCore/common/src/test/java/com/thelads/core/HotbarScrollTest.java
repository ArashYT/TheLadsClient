package com.thelads.core;

import com.thelads.core.client.HotbarScrollHelper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HotbarScrollTest {
    @Test
    void testStandardScroll() {
        // Normal scroll down (dir=1): slot 0 -> 8, or dir=-1: slot 0 -> 1
        assertEquals(1, HotbarScrollHelper.computeTargetSlot(0, -1, false, false));
        assertEquals(8, HotbarScrollHelper.computeTargetSlot(0, 1, false, false));
        assertEquals(0, HotbarScrollHelper.computeTargetSlot(8, -1, false, false));
    }

    @Test
    void testInvertedScroll() {
        // Inverted: dir=-1 becomes dir=1 (slot 0 -> 8)
        assertEquals(8, HotbarScrollHelper.computeTargetSlot(0, -1, true, false));
        assertEquals(1, HotbarScrollHelper.computeTargetSlot(0, 1, true, false));
    }

    @Test
    void testNoWrap() {
        // No wrap: cannot scroll left past 0, cannot scroll right past 8
        assertEquals(0, HotbarScrollHelper.computeTargetSlot(0, 1, false, true));
        assertEquals(8, HotbarScrollHelper.computeTargetSlot(8, -1, false, true));
        assertEquals(1, HotbarScrollHelper.computeTargetSlot(0, -1, false, true));
        assertEquals(7, HotbarScrollHelper.computeTargetSlot(8, 1, false, true));
    }
}
