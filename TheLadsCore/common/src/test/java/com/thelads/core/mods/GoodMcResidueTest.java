package com.thelads.core.mods;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GoodMcResidueTest {
    @Test
    void goodMcBaseIsResetOnlyWhenGoodMcIsGone() {
        assertTrue(GoodMcResidue.isResidue(32767.0, false));
        assertFalse(GoodMcResidue.isResidue(32767.0, true), "a loaded GoodMC owns the value");
    }

    @Test
    void otherBasesAreLeftAlone() {
        assertFalse(GoodMcResidue.isResidue(4.0, false), "vanilla default, also what GoodMC writes with old combat off");
        assertFalse(GoodMcResidue.isResidue(1024.0, false), "attribute maximum");
        assertFalse(GoodMcResidue.isResidue(32766.0, false));
        assertFalse(GoodMcResidue.isResidue(Math.nextDown(32767.0), false));
    }
}
