package com.thelads.core.perf;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SodiumPipelineHookTest {
    @BeforeEach
    void setUp() {
        SodiumPipelineHook.setDisabled(false);
        SodiumPipelineHook.setSodiumLoaded(false);
    }

    @Test
    void routingRequiresOptionAndSodiumLoaded() {
        assertFalse(SodiumPipelineHook.shouldUseSodiumPipeline());
        SodiumPipelineHook.setSodiumLoaded(true);
        assertTrue(SodiumPipelineHook.shouldUseSodiumPipeline());

        SodiumPipelineHook.setDisabled(true);
        assertFalse(SodiumPipelineHook.shouldUseSodiumPipeline());
    }
}
