package com.thelads.core.client;

import com.thelads.core.modules.SignalLossModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignalLossPolicyTest {
    private static final long MS = 1_000_000L;
    private final Object connection = new Object();
    private final SignalLossPolicy.Settings defaults = new SignalLossPolicy.Settings(2000, 2000, 1000);
    private SignalLossPolicy.Frame update(SignalLossPolicy policy, long now, long packet) {
        return policy.update(connection, now * MS, packet * MS, true, false, false, defaults);
    }
    @Test void joinGracePreventsEarlyWarningsAndExpiresAtFiveSeconds() {
        var policy = new SignalLossPolicy(); policy.joined(connection, 0);
        assertEquals(0, update(policy, 4999, 1).progress()); assertTrue(update(policy, 5000, 1).interrupted());
    }
    @Test void thresholdIsStrictlyGreaterThanConfiguredWholeMilliseconds() {
        var policy = new SignalLossPolicy(); policy.joined(connection, 0);
        assertFalse(update(policy, 10000, 8000).interrupted()); assertTrue(update(policy, 10001, 8000).interrupted());
    }
    @Test void recoveryRetainsFinalLagUntilBothMinimumAndLingerExpire() {
        var policy = new SignalLossPolicy(); policy.joined(connection, 0); update(policy, 5000, 1);
        var lag = update(policy, 6000, 1).seconds(); assertEquals(lag, update(policy, 6100, 6100).seconds());
        assertTrue(update(policy, 7050, 7050).lingering()); assertFalse(update(policy, 7100, 7100).lingering());
        assertEquals(lag, update(policy, 7101, 7101).seconds(), "fade keeps recovered duration instead of flashing 0s");
    }
    @Test void minimumDurationWinsWhenRecoveryIsQuick() {
        var policy = new SignalLossPolicy(); policy.joined(connection, 0); update(policy, 5000, 1);
        update(policy, 5001, 5001); assertTrue(update(policy, 6999, 6999).lingering()); assertFalse(update(policy, 7000, 7000).lingering());
    }
    @Test void renewedInterruptionCancelsRecoveryCountdown() {
        var policy = new SignalLossPolicy(); policy.joined(connection, 0); update(policy, 5000, 1); update(policy, 6000, 6000);
        var settings = new SignalLossPolicy.Settings(0, 10000, 10000);
        assertTrue(policy.update(connection, 6002 * MS, 6000 * MS, true, false, false, settings).interrupted());
    }
    @Test void pausedWorldCannotGenerateWarningOnResumeFromStalePacket() {
        var policy = new SignalLossPolicy(); policy.joined(connection, 0); update(policy, 5000, 1);
        assertEquals(0, policy.update(connection, 20000 * MS, MS, true, true, false, defaults).progress());
        assertEquals(0, update(policy, 20001, 1).progress()); assertTrue(update(policy, 22001, 1).interrupted());
    }
    @Test void moduleOffAndIneligibleSessionsImmediatelyClearWarning() {
        var policy = new SignalLossPolicy(); policy.joined(connection, 0); update(policy, 5000, 1);
        assertEquals(0, policy.update(connection, 6000 * MS, MS, false, false, false, defaults).progress());
        assertEquals(0, update(policy, 6001, 1).progress());
    }
    @Test void newConnectionDoesNotInheritOldToast() {
        var policy = new SignalLossPolicy(); policy.joined(connection, 0); update(policy, 5000, 1);
        assertEquals(0, policy.update(new Object(), 6000 * MS, MS, true, false, false, defaults).progress());
    }
    @Test void disconnectedConnectionClearsState() {
        var policy = new SignalLossPolicy(); policy.joined(connection, 0); update(policy, 5000, 1);
        assertEquals(0, policy.update(null, 6000 * MS, MS, true, false, false, defaults).progress());
    }
    @Test void reducedMotionShowsAndHidesWithoutSliding() {
        var policy = new SignalLossPolicy(); policy.joined(connection, 0);
        assertEquals(1, policy.update(connection, 5000 * MS, MS, true, false, true, defaults).progress());
        policy.update(connection, 5100 * MS, 5100 * MS, true, false, true, defaults);
        assertEquals(0, policy.update(connection, 7100 * MS, 7100 * MS, true, false, true, defaults).progress());
    }
    @Test void animationMatchesAtThirtySixtyAndTwoHundredFortyFramesPerSecond() {
        for (int fps : new int[]{30, 60, 240}) {
            var policy = new SignalLossPolicy(); policy.joined(connection, 0); update(policy, 5000, 5000);
            long start = 7_001_000_000L;
            policy.update(connection, start, 7_000_000_000L, true, false, false, defaults);
            var frame = new SignalLossPolicy.Frame(0, 0, false, false);
            for (int i = 1; i <= fps / 5; i++) frame = policy.update(connection, start + i * 1_000_000_000L / fps,
                4_000_000_000L, true, false, false, defaults);
            assertEquals(.8, frame.progress(), .0001);
        }
    }
    @Test void clockWrapAndNegativeOriginDoNotBreakGraceOrSilence() {
        for (long start : new long[]{-10_000 * MS, Long.MAX_VALUE - 2000 * MS}) {
            var policy = new SignalLossPolicy(); policy.joined(connection, start);
            assertTrue(policy.update(connection, start + 6000 * MS, start + MS, true, false, false, defaults).interrupted());
        }
    }
    @Test void integerTimingFieldsRetainFullRangeAndInvalidInputUsesSafeDefault() {
        var module = new SignalLossModule(); module.timeout.setValue("2147483647");
        assertEquals(Integer.MAX_VALUE, SignalLossModule.milliseconds(module.timeout, 2000));
        for (String invalid : new String[]{"", "-1", "abc", "2.5", "2147483648"}) {
            module.timeout.setValue(invalid); assertEquals(2000, SignalLossModule.milliseconds(module.timeout, 2000));
        }
        module.timeout.setValue("0"); assertEquals(0, SignalLossModule.milliseconds(module.timeout, 2000));
    }
}
