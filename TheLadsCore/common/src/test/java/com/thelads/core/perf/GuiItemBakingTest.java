package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GuiItemBakingTest {
    @BeforeEach
    void setUp() {
        GuiItemBaking.setDisabled(false);
    }

    @Test
    void staticItemsIdentifiedCorrectly() {
        assertTrue(GuiItemBaking.isStatic(1, false));
        assertTrue(GuiItemBaking.isStatic(0, false));
        assertFalse(GuiItemBaking.isStatic(2, false), "Animated textures with >1 frames are not static");
        assertFalse(GuiItemBaking.isStatic(1, true), "Items with enchantment glint are not static");
    }

    @Test
    void animationTicksAdvanceEpoch() {
        long initial = GuiItemBaking.getAnimationEpoch();
        GuiItemBaking.tickAnimation();
        assertEquals(initial + 1, GuiItemBaking.getAnimationEpoch());
    }

    @Test
    void disabledBakingRejectsStatic() {
        GuiItemBaking.setDisabled(true);
        assertFalse(GuiItemBaking.isStatic(1, false));
    }
}
