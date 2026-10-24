package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.client.pack.PackProfiles;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.modules.SurvivalGuardsModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Parity 1.9.19 utility rows: Survival Guards, Breeding Timer, Draggable Server List and Pack Profiles rules. */
class UtilityParityTest {
    @Test
    void survivalGuardsStopOnlyWornToolsAndLetSneakThrough() {
        SurvivalGuardsModule guards = new SurvivalGuardsModule();
        assertFalse(guards.stopsTool(false, 100, 99), "off by default");
        guards.setEnabled(true);
        assertTrue(guards.stopsTool(false, 1561, 1556), "5 uses left = the default limit");
        assertFalse(guards.stopsTool(false, 1561, 1555), "6 uses left");
        assertFalse(guards.stopsTool(true, 1561, 1560), "sneaking lets it through");
        assertFalse(guards.stopsTool(false, 0, 0), "an item that never wears");
        guards.durabilityLeft.setValue(20);
        assertTrue(guards.stopsTool(false, 250, 230));
        guards.antiToolBreak.set(false);
        assertFalse(guards.stopsTool(false, 250, 249));
        assertTrue(guards.stopsStrip(false));
        assertFalse(guards.stopsStrip(true));
        guards.antiAmethyst.set(false);
        assertFalse(guards.stopsAmethyst(false));
        guards.hideHere("Anti Strip Log");
        assertTrue(guards.hidden(guards.antiStripLog));
        assertFalse(guards.hidden(guards.antiToolBreak));
    }

    @Test
    void secondHeartsWithinLoveTimeIsTheBreed() {
        BreedingTimer.State fed = new BreedingTimer.State();
        assertFalse(fed.hearts(1000), "fed: falls in love");
        assertTrue(fed.inLove(1300));
        assertTrue(fed.hearts(1300), "hearts again while in love: bred");
        assertTrue(fed.cooling(1300 + 5999));
        assertFalse(fed.cooling(1300 + 6000));
        assertFalse(fed.inLove(1301), "breeding ends the love");

        BreedingTimer.State late = new BreedingTimer.State();
        late.hearts(0);
        assertFalse(late.hearts(601), "love ran out: fed again, not bred");
        assertFalse(late.cooling(601));
    }

    @Test
    void breedingLabelCountsDownWholeSeconds() {
        assertEquals(300, BreedingTimer.secondsLeft(6000, 0));
        assertEquals(1, BreedingTimer.secondsLeft(6000, 5999), "rounds up");
        assertEquals(0, BreedingTimer.secondsLeft(6000, 6000));
        assertEquals("❤ 5:00", BreedingTimer.label(300));
        assertEquals("❤ 0:09", BreedingTimer.label(9));
        BreedingTimer.State state = new BreedingTimer.State();
        state.bredAt(0);
        String first = state.label(0);
        assertSame(first, state.label(10), "same second: the same string, no new one per frame");
        assertEquals("❤ 4:59", state.label(20));
    }

    @Test
    void draggedServerMovesAndOthersShift() {
        List<String> servers = new ArrayList<>(List.of("a", "b", "c", "d"));
        ServerListDrag.move(0, 2, servers::get, servers::set);
        assertEquals(List.of("b", "c", "a", "d"), servers);
        ServerListDrag.move(3, 0, servers::get, servers::set);
        assertEquals(List.of("d", "b", "c", "a"), servers);
        ServerListDrag.move(1, 1, servers::get, servers::set);
        assertEquals(List.of("d", "b", "c", "a"), servers);
        assertEquals(1, ServerListDrag.target(-1, 1, 4), "off the rows: stays where it was");
        assertEquals(3, ServerListDrag.target(6, 1, 4), "the LAN rows under the saved ones: last saved");
        assertEquals(2, ServerListDrag.target(2, 0, 4));
        assertFalse(ServerListDrag.dragging(100, 104));
        assertTrue(ServerListDrag.dragging(100, 95));
    }

    @Test
    void profileKeepsItsOrderDropsMissingPacksAndKeepsRequiredOnes() {
        List<String> arranged = PackProfiles.arrange(List.of("file/PvP.zip", "file/Gone.zip", "file/Fonts.zip", "file/PvP.zip"),
            Set.of("file/PvP.zip", "file/Fonts.zip", "file/Other.zip", "vanilla", "fabric"), List.of("fabric", "vanilla"));
        assertEquals(List.of("file/PvP.zip", "file/Fonts.zip", "fabric", "vanilla"), arranged);
        assertEquals(List.of("file/Fonts.zip", "vanilla"),
            PackProfiles.arrange(List.of("file/Fonts.zip", "vanilla"), Set.of("file/Fonts.zip", "vanilla"), List.of("vanilla")));
    }

    @Test
    void profilesPersistPerVersion(@TempDir Path dir) throws Exception {
        ClientPaths.setBaseDir(dir);
        try {
            PackProfiles.reload();
            assertTrue(PackProfiles.save("1.20.1", " PvP ", List.of("file/a.zip", "vanilla")));
            assertFalse(PackProfiles.save("1.20.1", "   ", List.of("x")), "a name is needed");
            assertFalse(PackProfiles.save("1.20.1", "x".repeat(PackProfiles.MAX_NAME + 1), List.of("x")));
            PackProfiles.save("1.8.9", "PvP", List.of("a.zip"));
            PackProfiles.reload();
            assertEquals(List.of("PvP"), PackProfiles.names("1.20.1"));
            assertEquals(List.of("file/a.zip", "vanilla"), PackProfiles.get("1.20.1", "PvP"));
            assertEquals(List.of("a.zip"), PackProfiles.get("1.8.9", "PvP"));
            PackProfiles.delete("1.20.1", "PvP");
            PackProfiles.reload();
            assertTrue(PackProfiles.names("1.20.1").isEmpty());
            assertEquals(List.of(), PackProfiles.get("1.20.1", "PvP"));
            try (var files = Files.list(dir)) { assertEquals(1, files.count(), "no temporary files left"); }
        } finally {
            ClientPaths.setBaseDir(null);
            PackProfiles.reload();
        }
    }
}
