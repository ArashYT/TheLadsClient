package com.thelads.core.client;

import com.thelads.core.config.ActionOption;
import com.google.gson.JsonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectBehaviorTest {
    private static final long SECOND = 1_000_000_000L;
    @Test void finiteDelaysAreAttemptSpecificAndExhaustExactlyOnce() {
        var plan = new ReconnectPlan(); long now = 100;
        for (int delay : List.of(3, 10, 30, 60)) {
            assertTrue(plan.schedule(List.of(3, 10, 30, 60), false, now));
            assertEquals(delay, plan.secondsLeft(now)); assertFalse(plan.takeDue(now + delay * SECOND - 1));
            now += delay * SECOND; assertTrue(plan.takeDue(now)); assertFalse(plan.takeDue(now));
        }
        assertEquals(4, plan.attempts()); assertFalse(plan.schedule(List.of(3, 10, 30, 60), false, now));
    }
    @Test void infiniteRetriesRepeatOnlyFinalDelay() {
        var plan = new ReconnectPlan(); long now = 0;
        for (int i = 0; i < 20; i++) {
            int expected = i == 0 ? 3 : 10;
            assertTrue(plan.schedule(List.of(3, 10), true, now)); assertEquals(expected, plan.secondsLeft(now));
            now += expected * SECOND; assertTrue(plan.takeDue(now));
        }
    }
    @Test void resizingCannotRestartPendingCountdown() {
        var plan = new ReconnectPlan(); plan.schedule(List.of(3), false, 0);
        assertTrue(plan.schedule(List.of(3), false, 2 * SECOND)); assertEquals(1, plan.secondsLeft(2 * SECOND));
        assertTrue(plan.takeDue(3 * SECOND));
    }
    @Test void cancelClearsCountdownAttemptAndAutomaticJoinState() {
        var plan = new ReconnectPlan(); plan.schedule(List.of(1), false, 0); plan.takeDue(SECOND); plan.cancel();
        assertFalse(plan.pending()); assertFalse(plan.wasAutomatic()); assertEquals(0, plan.attempts()); assertFalse(plan.joined());
        assertFalse(plan.takeDue(10 * SECOND)); assertTrue(plan.schedule(List.of(1), false, 10 * SECOND));
    }
    @Test void successfulAutomaticJoinResetsRetrySequence() {
        var plan = new ReconnectPlan(); plan.schedule(List.of(1), false, 0); plan.takeDue(SECOND);
        assertTrue(plan.joined()); assertFalse(plan.joined()); assertTrue(plan.schedule(List.of(1), false, SECOND));
    }
    @Test void emptyOrInvalidDelayNeverCreatesRetry() {
        for (List<Integer> list : List.of(List.<Integer>of(), List.of(0), List.of(-1), List.of(86401)))
            assertFalse(new ReconnectPlan().schedule(list, true, 0));
    }
    @Test void monotonicClockCanBeNegativeAndWrap() {
        for (long now : List.of(-10 * SECOND, Long.MAX_VALUE - SECOND)) {
            var plan = new ReconnectPlan(); plan.schedule(List.of(3), false, now);
            assertEquals(3, plan.secondsLeft(now)); assertFalse(plan.takeDue(now + 2 * SECOND)); assertTrue(plan.takeDue(now + 3 * SECOND));
        }
    }
    @Test void keyConditionsMatchSubstringsAndModeInvertsResult() {
        var actual = List.of("multiplayer.disconnect.kicked"); var keys = List.of("disconnect.kicked");
        assertFalse(ReconnectFilters.allows(actual, "reason", keys, List.of(), false));
        assertTrue(ReconnectFilters.allows(actual, "reason", keys, List.of(), true));
        assertTrue(ReconnectFilters.allows(List.of("network.error"), "reason", keys, List.of(), false));
    }
    @Test void localizedReasonPatternsFindMatchesAndMalformedImportsAreSafe() {
        assertFalse(ReconnectFilters.allows(List.of(), "Server restarting in 10s", List.of(), List.of("[", "restarting"), false));
        assertTrue(ReconnectFilters.allows(List.of(), "Server gone", List.of(), List.of("["), false));
    }
    @Test void authenticationConsentAndTransferCannotBeOverriddenByFilters() {
        for (String key : List.of("disconnect.loginFailedInfo", "disconnect.loginFailedInfo.invalidSession", "multiplayer.disconnect.not_authenticated",
                "multiplayer.disconnect.invalid_public_key", "multiplayer.disconnect.code_of_conduct", "disconnect.transfer")) {
            assertFalse(ReconnectFilters.allows(List.of(key), "reason", List.of(), List.of(), false));
            assertFalse(ReconnectFilters.allows(List.of(key), "reason", List.of(key), List.of(".*"), true));
        }
    }
    @Test void actionContextsUseExactOrWholeRegexMatching() {
        assertTrue(ReconnectFilters.contextMatches("play.example:25565", "play.example:25565", false));
        assertFalse(ReconnectFilters.contextMatches("example", "play.example:25565", false));
        assertFalse(ReconnectFilters.contextMatches("example", "play.example:25565", true));
        assertTrue(ReconnectFilters.contextMatches(".*example:25565", "play.example:25565", true));
        assertFalse(ReconnectFilters.contextMatches("[", "anything", true));
    }
    @Test void actionsWaitBeforeFirstAndBetweenEachAndKeepStableProfileOrder() {
        var queue = new ReconnectActions(); var connection = new Object(); var account = UUID.randomUUID(); var sent = new ArrayList<String>();
        queue.begin(100); queue.add(connection, account, 1, List.of("first", "/second"), true);
        queue.add(connection, account, 1, List.of("same-time"), false);
        queue.drain(connection, account, true, 100 + SECOND - 1, (text, signed) -> sent.add(text)); assertTrue(sent.isEmpty());
        queue.drain(connection, account, true, 100 + SECOND, (text, signed) -> sent.add(text + ":" + signed));
        assertEquals(List.of("first:true", "same-time:false"), sent);
        queue.drain(connection, account, true, 100 + 2 * SECOND, (text, signed) -> sent.add(text + ":" + signed));
        assertEquals(List.of("first:true", "same-time:false", "/second:true"), sent); assertEquals(0, queue.size());
    }
    @Test void disablingActionsDropsAllPendingWork() {
        var queue = new ReconnectActions(); var connection = new Object(); var account = UUID.randomUUID();
        queue.begin(0); queue.add(connection, account, 1, List.of("mock only"), false);
        queue.drain(connection, account, false, SECOND, (text, signed) -> fail("disabled action")); assertEquals(0, queue.size());
    }
    @Test void actionsCannotCrossConnectionsOrAccounts() {
        var queue = new ReconnectActions(); var connection = new Object(); var account = UUID.randomUUID();
        queue.begin(0); queue.add(connection, account, 1, List.of("mock only"), false);
        queue.drain(new Object(), account, true, SECOND, (text, signed) -> fail("cross-connection action"));
        queue.add(connection, account, 1, List.of("mock only"), false);
        queue.drain(connection, UUID.randomUUID(), true, SECOND, (text, signed) -> fail("cross-account action"));
        assertEquals(0, queue.size());
    }
    @Test void actionsNeverTruncateAnOversizedCommandIntoDifferentMeaning() {
        var queue = new ReconnectActions(); var connection = new Object(); var account = UUID.randomUUID(); queue.begin(0);
        queue.add(connection, account, 1, List.of("x".repeat(257), "/" + "x".repeat(32767)), false);
        assertEquals(0, queue.size());
    }
    @Test void actionOffsetsStayOrderedAcrossClockWrap() {
        var queue = new ReconnectActions(); var connection = new Object(); var account = UUID.randomUUID(); var sent = new ArrayList<String>();
        long start = Long.MAX_VALUE - SECOND; queue.begin(start); queue.add(connection, account, 1, List.of("a", "b"), false);
        queue.drain(connection, account, true, start + 2 * SECOND, (text, signed) -> sent.add(text)); assertEquals(List.of("a", "b"), sent);
    }
    @Test void clearingActionsPreventsDispatchAndAllowsFreshSession() {
        var queue = new ReconnectActions(); var connection = new Object(); var account = UUID.randomUUID(); queue.begin(0);
        queue.add(connection, account, 1, List.of("mock only"), false); queue.clear();
        queue.drain(connection, account, true, 10 * SECOND, (text, signed) -> fail("cleared action"));
    }
    @Test void actionOptionsDoNotDeserializeCallbacksOrInventAvailability() {
        var option = new ActionOption("Editor", "Open"); assertFalse(option.isAvailable());
        assertEquals(JsonNull.INSTANCE, option.save()); option.load(JsonNull.INSTANCE); assertFalse(option.isAvailable());
        int[] invoked = {0}; option.setAction(() -> invoked[0]++); option.run(); assertEquals(1, invoked[0]);
    }
}
