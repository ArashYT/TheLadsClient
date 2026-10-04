package com.thelads.core.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectSessionTest {
    private static final long SECOND = 1_000_000_000L;
    private final UUID player = UUID.randomUUID();
    private final ReconnectSettings lists = new ReconnectSettings();

    private static List<String> keys(String... keys) { return List.of(keys); }

    @Test void countsDownThroughEachDelayAndKeepsTheAttemptAcrossItsOwnReconnects() {
        var session = new ReconnectSession(); int[] connects = {0};
        session.begin("play.example:25565", () -> { connects[0]++; session.begin("play.example:25565", () -> fail("replaced"), player); }, player);
        session.joined(null, lists, false, false, false, 0);
        long now = 0;
        for (int delay : List.of(3, 10, 30, 60)) {
            Object screen = new Object();
            session.disconnected(screen, keys("disconnect.timeout"), "Timed out", lists, false, false, now);
            assertEquals("Reconnect in " + delay + "s", session.retryLabel(now));
            assertFalse(session.due(screen, now + delay * SECOND - 1));
            now += delay * SECOND;
            assertTrue(session.due(screen, now));
            session.reconnect();
        }
        assertEquals(4, connects[0]);
        Object last = new Object();
        session.disconnected(last, keys("disconnect.timeout"), "Timed out", lists, false, false, now);
        assertFalse(session.counting());
        assertEquals("Retry limit reached · Reconnect", session.retryLabel(now));
    }

    @Test void kicksAndBansDoNotRetryByDefault() {
        for (var reason : List.of(List.of("multiplayer.disconnect.kicked", "Kicked by an operator"),
                List.of("multiplayer.disconnect.banned.reason", "You are banned from this server."),
                List.of("multiplayer.disconnect.not_whitelisted", "You are not white-listed on this server!"),
                List.of("", "You are banned from this network"), List.of("", "You have been kicked for idling"))) {
            var session = new ReconnectSession();
            session.begin("server", () -> fail("retried a kick"), player);
            session.joined(null, lists, false, false, false, 0);
            session.disconnected(new Object(), reason.get(0).isEmpty() ? keys() : keys(reason.get(0)), reason.get(1), lists, false, false, 0);
            assertFalse(session.counting(), reason.toString());
            assertEquals("Reconnect", session.retryLabel(0));
        }
        for (var key : List.of("disconnect.timeout", "disconnect.lost", "multiplayer.disconnect.server_shutdown", "multiplayer.disconnect.server_full")) {
            var session = new ReconnectSession();
            session.begin("server", () -> { }, player);
            session.joined(null, lists, false, false, false, 0);
            session.disconnected(new Object(), keys(key), "reason", lists, false, false, 0);
            assertTrue(session.counting(), key);
        }
    }

    @Test void initialFailuresRetryOnlyWhenAskedAndOnlyForTheSameAccount() {
        var session = new ReconnectSession();
        session.begin("server", () -> { }, player);
        assertFalse(session.canRetry(player, false));
        assertTrue(session.canRetry(player, true));
        assertFalse(session.canRetry(UUID.randomUUID(), true));
        session.joined(null, lists, false, false, false, 0);
        assertTrue(session.canRetry(player, false));
        session.clear();
        assertFalse(session.canRetry(player, true));
    }

    @Test void cancelStopsTheCountdownAndTheSameScreenNeverRestartsIt() {
        var session = new ReconnectSession(); Object screen = new Object();
        session.begin("world", () -> fail("cancelled retry ran"), player);
        session.joined(null, lists, false, false, false, 0);
        session.disconnected(screen, keys(), "Connection Lost", lists, false, false, 0);
        assertTrue(session.cancelCountdown());
        session.disconnected(screen, keys(), "Connection Lost", lists, false, false, SECOND); // resize re-inits the screen
        assertFalse(session.counting());
        assertFalse(session.due(screen, 100 * SECOND));
        assertFalse(session.cancelCountdown());
    }

    @Test void joinActionsFollowOnlyAnAutomaticReconnectToTheirTarget() {
        var action = new ReconnectSettings.JoinAction();
        action.target = "server"; action.lines = new ArrayList<>(List.of("/home")); action.enabled = true;
        lists.joinActions.add(action);
        var session = new ReconnectSession(); var connection = new Object(); var sent = new ArrayList<String>(); Object screen = new Object();
        session.begin("server", () -> { }, player);
        assertFalse(session.joined(connection, lists, true, false, false, 0));
        session.sendDue(connection, player, true, 10 * SECOND, (text, signed) -> sent.add(text));
        assertTrue(sent.isEmpty());
        session.disconnected(screen, keys(), "Connection Lost", lists, false, false, 0);
        assertTrue(session.due(screen, 3 * SECOND));
        assertTrue(session.joined(connection, lists, true, false, false, 4 * SECOND));
        session.sendDue(connection, player, true, 5 * SECOND, (text, signed) -> sent.add(text));
        assertEquals(List.of("/home"), sent);
    }

    @Test void legacyFilesLoadAndSaveUnderTheNewNames(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("reconnect.json");
        Files.write(file, ("{\"delays\":[2,5],\"conditionKeys\":[\"a.key\"],\"conditionPatterns\":[\"x+\"],"
            + "\"autoMessages\":[{\"id\":\"srv\",\"delay\":2.5,\"messages\":[\"hi\"],\"enabled\":true}]}").getBytes(StandardCharsets.UTF_8));
        var loaded = ReconnectSettings.load(file);
        assertEquals(List.of(2, 5), loaded.retryDelays);
        assertEquals(List.of("a.key"), loaded.reasonKeys);
        assertEquals(List.of("x+"), loaded.reasonPatterns);
        assertEquals("srv", loaded.joinActions.get(0).target);
        assertEquals(2.5, loaded.joinActions.get(0).interval);
        assertEquals(List.of("hi"), loaded.joinActions.get(0).lines);
        loaded.save(file);
        String saved = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(saved.contains("\"retryDelays\"") && !saved.contains("\"delays\""), saved);
        assertEquals(List.of(2, 5), ReconnectSettings.load(file).retryDelays);
        Files.write(file, "{broken".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of(3, 10, 30, 60), ReconnectSettings.load(file).retryDelays);
        assertEquals("{broken", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }
}
