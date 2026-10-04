package com.thelads.core.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SprintTraceTest {
    @Test void oneStopAndStartIsFineAFlickerIsNot() {
        SprintTrace trace = new SprintTrace();
        trace.phase("wall");
        boolean[] steady = {true, true, false, false, false, false, true, true};
        for (boolean sent : steady) trace.tick(sent, sent, "");
        assertTrue(trace.failures().isEmpty(), trace.failures().toString());
        assertEquals(2, trace.phaseChanges());
        trace.phase("hit");
        boolean[] spam = {true, false, true, false, true};
        for (boolean sent : spam) trace.tick(sent, sent, "");
        trace.finish();
        assertFalse(trace.failures().isEmpty());
        assertTrue(trace.summary().contains("wall 2; hit 4"), trace.summary());
    }
}
