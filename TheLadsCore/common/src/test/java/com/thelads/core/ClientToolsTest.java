package com.thelads.core;

import com.thelads.core.client.ClientTools;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ClientToolsTest {
    @Test void portalUsesFloorForNegativeCoordinatesAndLongMultiplication() {
        assertEquals("Nether: -1, -2", ClientTools.portal("minecraft:overworld", -1, -9));
        assertEquals("Overworld: -8, 17179869176", ClientTools.portal("minecraft:the_nether", -1, Integer.MAX_VALUE));
        assertEquals("", ClientTools.portal("minecraft:the_end", 5, 6));
    }
    @Test void stopwatchPausesResumesAndResetsWithoutWallClock() {
        var time = new AtomicLong(100_000_000);
        var watch = new ClientTools.Stopwatch(time::get);
        watch.toggle(); time.addAndGet(1_500_000_000); assertEquals(1500, watch.millis());
        watch.toggle(); time.addAndGet(9_000_000_000L); assertEquals(1500, watch.millis());
        watch.toggle(); time.addAndGet(500_000_000); assertEquals(2000, watch.millis());
        watch.reset(); assertEquals(0, watch.millis()); assertTrue(watch.running());
        watch.toggle(); watch.reset(); assertFalse(watch.running());
        assertEquals("25:01:01", ClientTools.duration(90_061_000));
    }
    @Test void warningsOnlyFireOnTransitionsAndRespectRecoveryCooldown() {
        var warning = new ClientTools.Warning();
        assertFalse(warning.update(false, 0, 30_000));
        assertTrue(warning.update(true, 1, 30_000));
        assertFalse(warning.update(true, 90_000, 30_000));
        assertFalse(warning.update(false, 90_001, 30_000));
        assertTrue(warning.update(true, 90_002, 30_000));
        warning.update(false, 90_003, 30_000);
        assertFalse(warning.update(true, 90_004, 30_000));
        warning.reset(); assertTrue(warning.update(true, 0, 30_000));
    }
    @Test void particleBudgetRejectsDistantEffectsWithoutConsumingNearbyQuota() {
        var budget = new ClientTools.ParticleBudget();
        assertFalse(budget.allow(1, 65, 8, 2));
        assertTrue(budget.allow(1, 64, 8, 2));
        assertTrue(budget.allow(1, 0, 8, 2));
        assertFalse(budget.allow(1, 0, 8, 2));
        assertTrue(budget.allow(2, 0, 8, 2));
        budget.reset(); assertTrue(budget.allow(2, 0, 8, 2));
    }
}
