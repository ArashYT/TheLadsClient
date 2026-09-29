package com.thelads.core.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ServerListMergeTest {
    record Server(String name, String ip, boolean hidden) {
        Server(String name, String ip) {
            this(name, ip, false);
        }
    }

    /** What the mixin fingerprints: every stored field (here the name) and the hidden flag. */
    private static final Function<Server, String> FINGERPRINT = server -> server.name() + (server.hidden() ? " hidden" : "");

    private static Map<String, String> loaded(Server... servers) {
        return ServerListMerge.snapshot(List.of(servers), Server::ip, FINGERPRINT);
    }

    private static ServerListMerge.Plan<Server> plan(Map<String, String> loaded, List<Server> current, List<Server> disk) {
        return ServerListMerge.plan(loaded, current, disk, true, Server::ip, FINGERPRINT);
    }

    private static final Server A = new Server("A", "a.example");
    private static final Server B = new Server("B", "b.example");
    private static final Server C = new Server("C", "c.example");

    @Test
    void serverAddedByAnotherGameIsAdded() {
        var result = plan(loaded(A), List.of(A), List.of(A, B));
        assertEquals(List.of(B), result.toAdd());
        assertTrue(result.toRemove().isEmpty() && result.toAdopt().isEmpty());
    }

    @Test
    void serverDeletedHereIsNotResurrectedFromDisk() {
        var result = plan(loaded(A, B), List.of(A), List.of(A, B));
        assertTrue(result.isEmpty());
    }

    @Test
    void serverDeletedByAnotherGameIsRemoved() {
        var result = plan(loaded(A, B), List.of(A, B), List.of(A));
        assertEquals(List.of(B), result.toRemove());
        assertTrue(result.toAdd().isEmpty());
    }

    @Test
    void serverAddedHereIsKeptWhenDiskLacksIt() {
        var result = plan(loaded(A), List.of(A, C), List.of(A));
        assertTrue(result.isEmpty());
    }

    @Test
    void addressEditedHereKeepsNewAddressAndDoesNotRestoreOldOne() {
        Server edited = new Server("A", "a2.example");
        var result = plan(loaded(A), List.of(edited), List.of(A));
        assertTrue(result.isEmpty());
    }

    @Test
    void addressesMatchTrimmedAndCaseInsensitive() {
        var result = plan(loaded(A), List.of(new Server("A", "  A.Example ")), List.of(A, new Server("B", "B.EXAMPLE")));
        assertEquals(List.of(new Server("B", "B.EXAMPLE")), result.toAdd());
        assertTrue(result.toRemove().isEmpty() && result.toAdopt().isEmpty());
        assertEquals("", ServerListMerge.key(null));
    }

    @Test
    void listThatNeverLoadedKeepsEverythingOnDisk() {
        // new ServerList(mc) + add + save without load(): vanilla would overwrite the shared file with one entry.
        var result = plan(Map.of(), List.of(C), List.of(A, B));
        assertEquals(List.of(A, B), result.toAdd());
        assertTrue(result.toRemove().isEmpty());
    }

    @Test
    void missingOrUnreadableDiskNeverRemovesServers() {
        var result = ServerListMerge.plan(loaded(A, B), List.of(A, B), List.of(), false, Server::ip, FINGERPRINT);
        assertTrue(result.isEmpty());
    }

    // core-java-2: another game deleting every server leaves a readable, empty servers.dat; that deletion must stick.
    @Test
    void readableEmptyDiskRemovesServersDeletedElsewhere() {
        assertEquals(List.of(A, B), plan(loaded(A, B), List.of(A, B), List.of()).toRemove());
        Server renamedHere = new Server("A renamed", "a.example");
        assertEquals(List.of(B), plan(loaded(A, B), List.of(renamedHere, B), List.of()).toRemove(),
            "a server edited here since the load is kept");
    }

    // core-java-1 (a): another game unhid and named the direct-connect entry; this game's stale hidden copy must not win.
    @Test
    void serverEditedElsewhereIsTakenWhenUnchangedHere() {
        Server directConnect = new Server("Minecraft Server", "play.example", true);
        Server added = new Server("Play", "play.example", false);
        var result = plan(loaded(A, directConnect), List.of(A, directConnect), List.of(A, added));
        assertEquals(List.of(new ServerListMerge.Adopt<>(directConnect, added)), result.toAdopt());
        assertTrue(result.toAdd().isEmpty() && result.toRemove().isEmpty());
    }

    // core-java-1 (b): this game's rename wins over another game's rename and survives its deletion.
    @Test
    void serverEditedHereWinsAndIsKeptWhenRemovedElsewhere() {
        Server renamedHere = new Server("C here", "c.example");
        assertTrue(plan(loaded(A, C), List.of(A, renamedHere), List.of(A)).isEmpty(), "not removed");
        assertTrue(plan(loaded(A, C), List.of(A, renamedHere), List.of(A, new Server("C there", "c.example"))).isEmpty(),
            "not overwritten");
    }

    @Test
    void snapshotAfterSaveFollowsDiskUnlessTheReadFailed() {
        Map<String, String> previous = loaded(A);
        assertEquals(loaded(A, C), ServerListMerge.snapshotAfterSave(previous, loaded(A, C), true));
        assertEquals(Map.of(), ServerListMerge.snapshotAfterSave(previous, Map.of(), true), "an empty readable file is empty");
        assertEquals(previous, ServerListMerge.snapshotAfterSave(previous, Map.of(), false));
    }

    @Test
    void twoGamesEditingConcurrentlyKeepEachOthersChanges() {
        List<Server> disk = new ArrayList<>(List.of(A, B));
        Game first = new Game(disk);
        Game second = new Game(disk);

        second.memory.add(C);
        disk = second.save(disk);
        first.memory.remove(B);
        disk = first.save(disk);
        assertEquals(List.of(A, C), disk);

        // The second game still shows B until it saves; its save must not bring B back and must keep its own edit.
        second.memory.add(new Server("D", "d.example"));
        disk = second.save(disk);
        assertEquals(List.of(A, C, new Server("D", "d.example")), disk);

        // A rename in the first game survives a save of the second game, which still holds the old name.
        first.memory.set(first.memory.indexOf(A), new Server("A renamed", "a.example"));
        disk = first.save(disk);
        disk = second.save(disk);
        assertEquals(List.of(new Server("A renamed", "a.example"), C, new Server("D", "d.example")), disk);
    }

    /** One running game: a list in memory plus the snapshot the mixin keeps. */
    private static final class Game {
        final List<Server> memory;
        Map<String, String> loaded;

        Game(List<Server> disk) {
            memory = new ArrayList<>(disk);
            loaded = ServerListMerge.snapshot(disk, Server::ip, FINGERPRINT);
        }

        List<Server> save(List<Server> disk) {
            var result = plan(loaded, memory, disk);
            memory.removeAll(result.toRemove());
            for (var adopt : result.toAdopt()) memory.set(memory.indexOf(adopt.current()), adopt.disk());
            memory.addAll(result.toAdd());
            List<Server> written = List.copyOf(memory);
            loaded = ServerListMerge.snapshotAfterSave(loaded, ServerListMerge.snapshot(written, Server::ip, FINGERPRINT), true);
            return written;
        }
    }
}
