package com.thelads.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LadsVersionTest {
    @Test void versionComesFromTheBuild() {
        assertTrue(LadsVersion.VERSION.matches("[0-9]+[.][0-9]+[.][0-9]+.*"), LadsVersion.VERSION);
        assertEquals("The Lads Client " + LadsVersion.VERSION, LadsVersion.clientName());
    }
}
