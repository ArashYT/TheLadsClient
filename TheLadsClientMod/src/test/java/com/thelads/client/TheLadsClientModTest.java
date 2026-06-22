package com.thelads.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class TheLadsClientModTest {
    @BeforeEach
    public void setupMocks() throws Exception { com.thelads.client.e2e.ReflectionHelper.setupMocks(); }
    @AfterEach
    public void teardownMocks() { com.thelads.client.e2e.ReflectionHelper.teardownMocks(); }

    @Test
    public void testModId() {
        assertEquals("theladsclientmod", TheLadsClientMod.MOD_ID, "MOD_ID should match the expected value.");
    }
}
