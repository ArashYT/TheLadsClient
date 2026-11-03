package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thelads.core.client.gui.ListContextMenu;
import com.thelads.core.shared.ServerListMerge;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The multiplayer list's rules: pinned network, drag target and gap, columns, version label and button, menus. */
class ServerListUiTest {
    private static final java.util.function.ToIntFunction<String> SIX_WIDE = text -> text.length() * 6;

    private static List<String> ids(List<ListContextMenu.Item> items) {
        return items.stream().map(ListContextMenu.Item::id).toList();
    }

    @Test
    void networkSelfHealsToTheTop() {
        List<String> moved = new ArrayList<>(List.of("a", "net", "b", "net"));
        assertTrue(ServerListMerge.pinFirst(moved, "net"::equals, () -> "new"));
        assertEquals(List.of("net", "a", "b"), moved, "moved up, the second copy removed");
        assertFalse(ServerListMerge.pinFirst(moved, "net"::equals, () -> "new"), "already first: nothing to save");
        List<String> missing = new ArrayList<>(List.of("a"));
        assertTrue(ServerListMerge.pinFirst(missing, "net"::equals, () -> "net"));
        assertEquals(List.of("net", "a"), missing);
    }

    @Test
    void onlyTheNetworkAddressIsPinnedAndNotInAModpack() {
        assertTrue(LadsNetwork.isPinned(" MC.arashyt.ca "));
        assertFalse(LadsNetwork.isPinned("lobby.arashyt.ca"));
        assertEquals(1, ServerListDrag.firstMovable("mc.arashyt.ca"));
        assertEquals(0, ServerListDrag.firstMovable("mc.hypixel.net"));
        System.setProperty("lads.modpack", "true");
        try {
            assertFalse(LadsNetwork.isPinned("mc.arashyt.ca"), "a modpack's list is its own");
            assertEquals(0, ServerListDrag.firstMovable("mc.arashyt.ca"));
        } finally {
            System.clearProperty("lads.modpack");
        }
    }

    @Test
    void nothingLandsAboveThePinnedRow() {
        assertEquals(1, ServerListDrag.target(0, 2, 5, 1), "over the network: the row under it");
        assertEquals(0, ServerListDrag.target(0, 2, 5, 0), "no pin: the top is free");
        assertEquals(4, ServerListDrag.target(7, 2, 5, 1), "LAN rows: last saved");
        assertEquals(2, ServerListDrag.target(-1, 2, 5, 1), "off the rows: stays");
        assertFalse(ServerListDrag.canSwap(1, 0, 1));
        assertFalse(ServerListDrag.canSwap(0, 1, 1));
        assertTrue(ServerListDrag.canSwap(1, 2, 1));
        assertTrue(ServerListDrag.canSwap(1, 0, 0));
    }

    @Test
    void othersOpenAGapWhereTheRowLands() {
        // Row 1 dragged to slot 3: rows 2 and 3 close up, slot 3 stays free for it.
        assertEquals(List.of(0, 3, 1, 2, 4), List.of(0, 1, 2, 3, 4).stream().map(i -> ServerListDrag.slot(i, 1, 3)).toList());
        // Row 3 dragged to slot 1.
        assertEquals(List.of(0, 2, 3, 1, 4), List.of(0, 1, 2, 3, 4).stream().map(i -> ServerListDrag.slot(i, 3, 1)).toList());
        assertEquals(2, ServerListDrag.slot(2, -1, -1), "no drag: where it is");
        assertTrue(ServerListDrag.dragging(100, 100, 105, 100), "sideways counts");
        assertFalse(ServerListDrag.dragging(100, 100, 104, 96), "inside the threshold");
    }

    @Test
    void columnsOnlyWhenEachStaysReadable() {
        assertEquals(1, ServerListColumns.most(427), "854x480 at scale 2");
        assertEquals(2, ServerListColumns.most(480), "1920 wide at scale 4");
        assertEquals(3, ServerListColumns.most(640), "1920 wide at scale 3");
        assertEquals(2, ServerListColumns.fit(3, 480), "falls back");
        assertEquals(3, ServerListColumns.fit(3, 960), "and comes back");
        assertEquals(1, ServerListColumns.fit(0, 960));
        assertEquals(305, ServerListColumns.width(3, 1920), "never wider than a vanilla row");
        assertTrue(ServerListColumns.width(3, 640) >= ServerListColumns.MIN_WIDTH);
        assertEquals(305, ServerListColumns.width(1, 300), "one column stays vanilla's");
        assertEquals(2, ServerListColumns.cycled(1, 640));
        assertEquals(3, ServerListColumns.cycled(2, 640));
        assertEquals(1, ServerListColumns.cycled(3, 640));
        assertEquals(1, ServerListColumns.cycled(2, 480), "3 is not offered when it does not fit");
    }

    @Test
    void columnsFillLeftToRightThenDown() {
        assertEquals(3, ServerListColumns.rows(7, 3));
        assertEquals(2, ServerListColumns.rows(6, 3));
        // Slot 4 of 3 columns: second row, second column.
        assertEquals(10 + 204, ServerListColumns.x(4, 3, 10, 200));
        assertEquals(40 + 36, ServerListColumns.y(4, 3, 40, 36));
        assertEquals(4, ServerListColumns.slotAt(10 + 204 + 5, 40 + 36 + 5, 10, 40, 200, 36, 3, 7));
        assertEquals(0, ServerListColumns.slotAt(10 + 202, 41, 10, 40, 200, 36, 3, 7), "the gap belongs to the left column");
        assertEquals(-1, ServerListColumns.slotAt(10 + 204 + 5, 40 + 72 + 5, 10, 40, 200, 36, 3, 7), "past the last row's end");
        assertEquals(-1, ServerListColumns.slotAt(5, 41, 10, 40, 200, 36, 3, 7), "left of the grid");
        assertEquals(-1, ServerListColumns.slotAt(10 + 3 * 204 + 1, 41, 10, 40, 200, 36, 3, 7), "right of the grid");
    }

    @Test
    void arrowKeysMoveInTheGrid() {
        // 3 columns, 7 rows: 0 1 2 / 3 4 5 / 6
        assertEquals(4, ServerListColumns.next(1, 7, 3, 0, 1));
        assertEquals(-1, ServerListColumns.next(1, 7, 3, 0, -1), "top edge");
        assertEquals(6, ServerListColumns.next(4, 7, 3, 0, 1), "nothing below: the last row");
        assertEquals(-1, ServerListColumns.next(6, 7, 3, 0, 1), "bottom edge");
        assertEquals(2, ServerListColumns.next(1, 7, 3, 1, 0));
        assertEquals(-1, ServerListColumns.next(2, 7, 3, 1, 0), "right edge");
        assertEquals(-1, ServerListColumns.next(3, 7, 3, -1, 0), "left edge");
        assertEquals(-1, ServerListColumns.next(6, 7, 3, 1, 0), "nothing beside it");
    }

    @Test
    void versionLabelAndButtonByState() {
        assertEquals(ServerListEntryUi.Version.NONE, ServerListEntryUi.version(false, true, true), "not pinged yet");
        assertEquals(ServerListEntryUi.Version.MUTED, ServerListEntryUi.version(true, false, true));
        assertEquals(ServerListEntryUi.Version.RED, ServerListEntryUi.version(true, true, false), "no ViaFabricPlus or unsupported: no button");
        assertEquals(ServerListEntryUi.Version.RED_WITH_BUTTON, ServerListEntryUi.version(true, true, true));
    }

    @Test
    void versionNeverOverlapsNameOrPlayerCount() {
        assertArrayEquals(new int[] {60, 40}, ServerListEntryUi.firstLine(301, 60, 40, 30), "room for both");
        // Content 196, count 30: room = 196 - 35 - 20 - 30 - 12 = 99.
        int[] tight = ServerListEntryUi.firstLine(196, 200, 60, 30);
        assertEquals(99, tight[0] + tight[1], "together they fill the room exactly");
        assertEquals(39, tight[1], "a long name leaves the version 2/5");
        assertArrayEquals(new int[] {60, 39}, ServerListEntryUi.firstLine(196, 80, 60, 30), "neither fits: both clip, the version keeps 2/5");
        assertArrayEquals(new int[] {40, 59}, ServerListEntryUi.firstLine(196, 40, 80, 30), "a short name keeps its width");
        assertArrayEquals(new int[] {0, 0}, ServerListEntryUi.firstLine(40, 50, 50, 30), "no room at all");
        assertEquals("abc..", ServerListEntryUi.clip("abcdefgh", 30, SIX_WIDE));
        assertEquals("abcdefgh", ServerListEntryUi.clip("abcdefgh", 48, SIX_WIDE));
        assertEquals("", ServerListEntryUi.clip("abcdefgh", 6, SIX_WIDE));
    }

    @Test
    void buttonShortensOnlyWhenItCannotFit() {
        assertEquals(ServerListEntryUi.CHANGE_VERSION, ServerListEntryUi.buttonLabel(320, SIX_WIDE));
        assertEquals("Change version & reconnect", ServerListEntryUi.buttonLabel(220, SIX_WIDE));
        assertEquals("Change version", ServerListEntryUi.buttonLabel(146, SIX_WIDE));
        assertNull(ServerListEntryUi.buttonLabel(100, SIX_WIDE));
    }

    @Test
    void menuItemsByEntryState() {
        assertEquals(List.of("join", "edit", "delete", "copy", "refresh"), ids(ServerListEntryUi.serverMenu(false, false)));
        assertEquals(List.of("join", "edit", "delete", "copy", "refresh", "version"), ids(ServerListEntryUi.serverMenu(false, true)));
        assertEquals(List.of("join", "copy", "refresh"), ids(ServerListEntryUi.serverMenu(true, false)), "the pinned entry cannot be edited or deleted");
        assertEquals(List.of("join", "copy", "refresh", "version"), ids(ServerListEntryUi.serverMenu(true, true)), "same as its row's button");
        assertEquals(128, ServerListEntryUi.clip("x".repeat(30000), 130, String::length).length(), "a server's long version is cut before it is measured");
        List<ListContextMenu.Item> world = ServerListEntryUi.worldMenu(false, false, true, false);
        assertEquals(List.of("play", "edit", "delete", "recreate", "folder", "copy"), ids(world));
        assertFalse(world.get(0).enabled(), "a locked world cannot be played");
        assertTrue(world.get(2).enabled());
        assertTrue(world.get(4).enabled());
    }

    @Test
    void menuStaysInsideTheScreenAndClosesOnOutsideClick() {
        assertArrayEquals(new int[] {100, 50}, ListContextMenu.place(100, 50, 96, 60, 640, 360));
        assertArrayEquals(new int[] {544, 300}, ListContextMenu.place(630, 350, 96, 60, 640, 360));
        ListContextMenu menu = new ListContextMenu();
        List<String> picked = new ArrayList<>();
        assertFalse(menu.mouseClicked(0, 0), "closed: not its click");
        menu.open(100, 50, ServerListEntryUi.worldMenu(false, true, true, true), picked::add);
        assertTrue(menu.mouseClicked(100, 58), "a disabled row keeps the click");
        assertTrue(menu.isOpen());
        assertTrue(picked.isEmpty());
        assertTrue(menu.mouseClicked(100, 74));
        assertFalse(menu.isOpen());
        assertEquals(List.of("edit"), picked);
        menu.open(100, 50, ServerListEntryUi.lanMenu(), picked::add);
        assertTrue(menu.mouseClicked(5, 5), "outside: closes, the click goes no further");
        assertFalse(menu.isOpen());
        assertEquals(1, picked.size());
    }

    @Test
    void noViaFabricPlusMeansNoForce() {
        assertFalse(ViaForce.available());
        assertFalse(ViaForce.supports(47));
        assertFalse(ViaForce.force(new Object(), 47));
        assertNull(ViaForce.forced(new Object()));
    }
}
