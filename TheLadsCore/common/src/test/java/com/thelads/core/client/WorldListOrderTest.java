package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldListOrderTest {
    private static final class Entry {
        final String id;
        final long lastPlayed;

        Entry(String id, long lastPlayed) {
            this.id = id;
            this.lastPlayed = lastPlayed;
        }
    }

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ClientPaths.setBaseDir(tempDir);
        Module m = ModuleManager.getInstance().getModule(ServerListDrag.NAME);
        if (m != null) {
            m.setEnabled(true);
        }
    }

    @AfterEach
    void tearDown() {
        Module m = ModuleManager.getInstance().getModule(ServerListDrag.NAME);
        if (m != null) {
            m.setEnabled(true);
        }
        ClientPaths.setBaseDir(null);
    }

    private static List<String> ids(List<Entry> list) {
        return list.stream().map(e -> e.id).collect(Collectors.toList());
    }

    @Test
    void emptySavedOrderSortsNewestLastPlayedFirst() {
        List<Entry> input = Arrays.asList(
                new Entry("alpha", 100L),
                new Entry("charlie", 300L),
                new Entry("bravo", 200L));
        List<Entry> sorted = WorldListOrder.sort(input, Collections.emptyList(), e -> e.id, e -> e.lastPlayed);
        assertEquals(Arrays.asList("charlie", "bravo", "alpha"), ids(sorted));
    }

    @Test
    void savedOrderOverridesLastPlayedOrder() {
        List<Entry> input = Arrays.asList(
                new Entry("charlie", 300L),
                new Entry("bravo", 200L),
                new Entry("alpha", 100L));
        List<Entry> sorted = WorldListOrder.sort(
                input, Arrays.asList("alpha", "charlie", "bravo"), e -> e.id, e -> e.lastPlayed);
        assertEquals(Arrays.asList("alpha", "charlie", "bravo"), ids(sorted));
    }

    @Test
    void newWorldsAppearAtTopSortedByLastPlayedAheadOfSavedWorlds() {
        List<Entry> input = Arrays.asList(
                new Entry("saved_first", 900L),
                new Entry("new_older", 150L),
                new Entry("saved_second", 800L),
                new Entry("new_newer", 250L));
        List<Entry> sorted = WorldListOrder.sort(
                input, Arrays.asList("saved_second", "saved_first"), e -> e.id, e -> e.lastPlayed);
        assertEquals(Arrays.asList("new_newer", "new_older", "saved_second", "saved_first"), ids(sorted));
    }

    @Test
    void saveAndLoadRoundTripAndCorruptJsonFallback() throws Exception {
        WorldListOrder.saveOrder(Arrays.asList("w3", "w1", "w2", "w1"));
        assertTrue(ClientPaths.getWorldOrderFile().isFile());
        assertEquals(Arrays.asList("w3", "w1", "w2"), WorldListOrder.loadOrder());

        List<Entry> input = Arrays.asList(new Entry("w1", 500L), new Entry("w2", 400L), new Entry("w3", 100L));
        assertEquals(Arrays.asList("w3", "w1", "w2"), ids(WorldListOrder.sortedCopy(input, e -> e.id, e -> e.lastPlayed)));

        Files.write(ClientPaths.getWorldOrderFile().toPath(), "{not-json".getBytes(StandardCharsets.UTF_8));
        assertEquals(Collections.emptyList(), WorldListOrder.loadOrder());
        assertEquals(Arrays.asList("w1", "w2", "w3"), ids(WorldListOrder.sortedCopy(input, e -> e.id, e -> e.lastPlayed)));
    }

    @Test
    void canDragDisabledWhenFilterActiveOrModuleOff() {
        assertTrue(WorldListOrder.canDrag(null));
        assertTrue(WorldListOrder.canDrag(""));
        assertTrue(WorldListOrder.canDrag("   "));
        assertFalse(WorldListOrder.canDrag("Survival"));

        Module m = ModuleManager.getInstance().getModule(ServerListDrag.NAME);
        m.setEnabled(false);
        assertFalse(WorldListOrder.canDrag(""));
    }

    @Test
    void pinnedWorldsComeFirstAndSurviveSerialize() {
        List<String> worlds = Arrays.asList("a", "b", "c", "d");
        assertEquals(Arrays.asList("c", "a", "b", "d"),
            WorldListOrder.pinnedFirst(Arrays.asList("a", "b", "c", "d"), Arrays.asList("c"), w -> w));
        assertEquals(worlds, WorldListOrder.pinnedFirst(worlds, Collections.<String>emptyList(), w -> w));
        String json = WorldListOrder.serializeOrder(worlds, Arrays.asList("b", "b", "d"));
        assertEquals(Arrays.asList("b", "d"), WorldListOrder.parsePinned(json));
        assertEquals(worlds, WorldListOrder.parseOrder(json));
        assertEquals(Collections.<String>emptyList(), WorldListOrder.parsePinned("[\"a\"]"));
    }

    @Test
    void pinHitIsTheTopRightCorner() {
        assertTrue(WorldListOrder.pinHit(0, 0, 200, 190, 6));
        assertFalse(WorldListOrder.pinHit(0, 0, 200, 10, 6));
    }
}
