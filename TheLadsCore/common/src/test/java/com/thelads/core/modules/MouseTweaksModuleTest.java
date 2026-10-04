package com.thelads.core.modules;

import static com.thelads.core.modules.MouseTweaksModule.LEFT;
import static com.thelads.core.modules.MouseTweaksModule.RIGHT;
import static org.junit.jupiter.api.Assertions.*;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The drag and wheel rules against a small inventory that clicks like vanilla's PICKUP and QUICK_MOVE. */
class MouseTweaksModuleTest {
    static final class Slot {
        final String name; String item; int count, part, y, limit = 64; boolean output;
        Slot(String name, int part, int y, String item, int count) { this.name = name; this.part = part; this.y = y; this.item = item; this.count = count; }
        void set(String item, int count) { this.item = count == 0 ? null : item; this.count = count; }
    }

    static final class Inv implements MouseTweaksModule.Menu<Slot, Slot> {
        final List<Slot> slots = new ArrayList<>();
        final Slot cursor = new Slot("cursor", 0, 0, null, 0);
        final List<String> clicks = new ArrayList<>();
        Slot add(int part, int y, String item, int count) { Slot s = new Slot("s" + slots.size(), part, y, item, count); slots.add(s); return s; }
        public List<Slot> slots() { return slots; }
        public Slot item(Slot slot) { return slot; }
        public Slot carried() { return cursor; }
        public int count(Slot item) { return item.count; }
        public boolean same(Slot a, Slot b) { return a.count > 0 && b.count > 0 && a.item.equals(b.item); }
        public boolean mayPlace(Slot slot, Slot item) { return !slot.output; }
        public int limit(Slot slot, Slot item) { return slot.limit; }
        public int part(Slot slot) { return slot.part; }
        public int y(Slot slot) { return slot.y; }

        public void click(Slot s, int button, boolean quickMove) {
            clicks.add(s.name + (quickMove ? " quick" : button == LEFT ? " L" : " R"));
            Slot c = cursor;
            if (quickMove) { s.set(null, 0); return; } // where it lands is the server's business
            if (c.count == 0) { // pick up all (left) or half (right)
                int take = button == LEFT ? s.count : (s.count + 1) / 2;
                c.set(s.item, take); s.set(s.item, s.count - take);
            } else if (s.output) { // an output slot gives its stack to a matching cursor
                if (same(s, c) && c.count + s.count <= 64) { c.set(c.item, c.count + s.count); s.set(null, 0); }
            } else if (s.count == 0 || same(s, c)) {
                int move = Math.min(button == LEFT ? c.count : 1, s.limit - s.count);
                s.set(c.item, s.count + move); c.set(c.item, c.count - move);
            } else { // different items swap
                String item = s.item; int count = s.count;
                s.set(c.item, c.count); c.set(item, count);
            }
        }
    }

    private static MouseTweaksModule module() { return new MouseTweaksModule(); }

    @Test
    void rightDragPutsOneItemInEverySlotEnteredAndAgainOnEachVisit() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot a = inv.add(1, 0, null, 0), b = inv.add(1, 0, null, 0), stone = inv.add(1, 0, "stone", 3);
        inv.cursor.set("dirt", 10);
        assertTrue(mt.press(inv, RIGHT, a), "the tweak owns a right press with an item, so vanilla's split never starts");
        mt.drag(inv, RIGHT, a, false); // still on the pressed slot
        mt.drag(inv, RIGHT, b, false);
        mt.drag(inv, RIGHT, stone, false); // another item: left alone
        mt.drag(inv, RIGHT, null, false);
        mt.drag(inv, RIGHT, a, false); // a second visit puts a second item
        assertTrue(mt.release(RIGHT), "the release must not click again");
        assertEquals(List.of("s0 R", "s1 R", "s0 R"), inv.clicks);
        assertEquals(2, a.count);
        assertEquals(1, b.count);
        assertEquals(3, stone.count);
        assertEquals(7, inv.cursor.count);
        mt.drag(inv, RIGHT, b, false);
        assertEquals(3, inv.clicks.size(), "nothing after the release");
    }

    @Test
    void rightDragStopsAtFullSlotsAndWhenTheCursorRunsOut() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot a = inv.add(1, 0, null, 0), full = inv.add(1, 0, "dirt", 64), one = inv.add(1, 0, "dirt", 1), b = inv.add(1, 0, null, 0);
        Slot c = inv.add(1, 0, null, 0), output = inv.add(2, 0, null, 0), single = inv.add(1, 0, null, 0);
        one.limit = 1;
        single.limit = 1; // as for an unstackable item such as a bundle, whose right click would drop its contents
        output.output = true;
        inv.cursor.set("dirt", 2);
        mt.press(inv, RIGHT, a);
        mt.drag(inv, RIGHT, full, false);
        mt.drag(inv, RIGHT, one, false); // a slot holding one item at most
        mt.drag(inv, RIGHT, output, false);
        mt.drag(inv, RIGHT, single, false);
        mt.drag(inv, RIGHT, b, false);
        mt.drag(inv, RIGHT, c, false); // the cursor is empty now
        assertEquals(List.of("s0 R", "s3 R"), inv.clicks);
        assertEquals(0, c.count);
    }

    @Test
    void rightPressWithoutAnItemOrWithTheTweakOffIsVanillas() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot a = inv.add(1, 0, "dirt", 4), b = inv.add(1, 0, null, 0);
        assertFalse(mt.press(inv, RIGHT, a));
        assertFalse(mt.release(RIGHT));
        ((BoolOption) mt.getOption("Right-drag places items")).set(false);
        inv.cursor.set("dirt", 5);
        assertFalse(mt.press(inv, RIGHT, b));
        mt.drag(inv, RIGHT, a, false);
        assertFalse(mt.release(RIGHT));
        assertTrue(inv.clicks.isEmpty());
    }

    @Test
    void leftDragGathersMatchingStacksThatFitTheCursor() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot a = inv.add(1, 0, "dirt", 10), b = inv.add(1, 0, "dirt", 20), stone = inv.add(1, 0, "stone", 5),
            big = inv.add(1, 0, "dirt", 40);
        assertFalse(mt.press(inv, LEFT, a), "the screen picks up the pressed stack itself");
        inv.click(a, LEFT, false); // vanilla's own click
        inv.clicks.clear();
        mt.drag(inv, LEFT, b, false);
        mt.drag(inv, LEFT, stone, false);
        mt.drag(inv, LEFT, big, false); // 30 + 40 would overflow the cursor
        assertFalse(mt.release(LEFT), "vanilla skips its own release after the press");
        assertEquals(List.of("s1 L", "s1 L"), inv.clicks);
        assertEquals(30, inv.cursor.count);
        assertEquals(0, b.count);
        assertEquals(5, stone.count);
        assertEquals(40, big.count);
    }

    @Test
    void leftDragTakesFromAnOutputSlotInOneClick() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot a = inv.add(1, 0, "planks", 4), result = inv.add(2, 0, "planks", 4);
        result.output = true;
        mt.press(inv, LEFT, a);
        inv.click(a, LEFT, false);
        inv.clicks.clear();
        mt.drag(inv, LEFT, result, false);
        assertEquals(List.of("s1 L"), inv.clicks);
        assertEquals(8, inv.cursor.count);
    }

    @Test
    void shiftDragWithAnItemMovesOnlyMatchingStacks() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot a = inv.add(1, 0, "dirt", 10), b = inv.add(1, 0, "dirt", 20), stone = inv.add(1, 0, "stone", 5);
        mt.press(inv, LEFT, a);
        inv.click(a, LEFT, false);
        inv.clicks.clear();
        mt.drag(inv, LEFT, b, true);
        mt.drag(inv, LEFT, stone, true);
        assertEquals(List.of("s1 quick"), inv.clicks);
        assertEquals(10, inv.cursor.count, "the cursor keeps its stack");
    }

    @Test
    void shiftDragWithoutAnItemMovesEverySlotEntered() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot empty = inv.add(1, 0, null, 0), dirt = inv.add(1, 0, "dirt", 3), stone = inv.add(1, 0, "stone", 5), gap = inv.add(1, 0, null, 0);
        Slot later = inv.add(1, 0, "sand", 2);
        assertFalse(mt.press(inv, LEFT, empty));
        mt.drag(inv, LEFT, dirt, true);
        mt.drag(inv, LEFT, stone, true);
        mt.drag(inv, LEFT, gap, true);
        mt.drag(inv, LEFT, later, false); // shift let go: no more moves
        assertEquals(List.of("s1 quick", "s2 quick"), inv.clicks);
        ((BoolOption) mt.getOption("Shift-drag moves items")).set(false);
        mt.press(inv, LEFT, empty);
        mt.drag(inv, LEFT, later, true);
        assertEquals(2, inv.clicks.size());
    }

    @Test
    void leftPressWithAnItemKeepsVanillaSplitting() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot a = inv.add(1, 0, null, 0), b = inv.add(1, 0, "dirt", 3);
        inv.cursor.set("dirt", 10);
        assertFalse(mt.press(inv, LEFT, a));
        mt.drag(inv, LEFT, b, true);
        assertFalse(mt.release(LEFT));
        assertTrue(inv.clicks.isEmpty());
    }

    @Test
    void wheelDownSendsOneItemToAMatchingStackInTheOtherInventory() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot chestEmpty = inv.add(2, 18, null, 0), chestDirt = inv.add(2, 18, "dirt", 3), player = inv.add(1, 84, "dirt", 5);
        inv.add(1, 84, "stone", 9); // same inventory as the scrolled slot: never a target
        assertTrue(mt.scroll(inv, player, false));
        assertEquals(List.of("s2 L", "s1 R", "s2 L"), inv.clicks);
        assertEquals(4, player.count);
        assertEquals(4, chestDirt.count);
        assertEquals(0, chestEmpty.count);
        assertEquals(0, inv.cursor.count, "the cursor ends empty");
        inv.clicks.clear();
        chestDirt.set("dirt", 64);
        mt.scroll(inv, player, false);
        assertEquals(1, chestEmpty.count, "a full stack is skipped for the first free slot");
        player.set("dirt", 1);
        inv.clicks.clear();
        chestEmpty.set(null, 0);
        mt.scroll(inv, player, false);
        assertEquals(List.of("s2 L", "s0 R"), inv.clicks, "the last item needs no put-back click");
    }

    @Test
    void wheelUpPullsOneMatchingItemSearchingInTheChosenOrder() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot chest = inv.add(2, 18, "dirt", 3), first = inv.add(1, 84, "dirt", 5), middle = inv.add(1, 84, "stone", 5), last = inv.add(1, 142, "dirt", 7);
        assertTrue(mt.scroll(inv, chest, true));
        assertEquals(List.of("s3 L", "s0 R", "s3 L"), inv.clicks, "last slot first by default (the hotbar in a chest)");
        assertEquals(4, chest.count);
        assertEquals(6, last.count);
        ((DropdownOption) mt.getOption("Scroll pulls from")).setIndex(0);
        mt.scroll(inv, chest, true);
        assertEquals(4, first.count);
        assertEquals(5, middle.count);
        chest.set("dirt", 64);
        inv.clicks.clear();
        assertTrue(mt.scroll(inv, chest, true), "a full slot still takes the turn");
        assertTrue(inv.clicks.isEmpty());
    }

    @Test
    void wheelSendsAWholeOutputOnlyWhereItFits() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot result = inv.add(2, 30, "planks", 4), nearlyFull = inv.add(1, 84, "planks", 62), free = inv.add(1, 84, null, 0);
        result.output = true;
        mt.scroll(inv, result, false);
        assertEquals(List.of("s0 L", "s2 L"), inv.clicks, "one craft, put down whole; never clicked again (that would craft more)");
        assertEquals(4, free.count);
        assertEquals(62, nearlyFull.count);
    }

    @Test
    void wheelLeavesOtherCasesToTheScreen() {
        MouseTweaksModule mt = module();
        Inv inv = new Inv();
        Slot alone = inv.add(1, 84, "dirt", 5), none = inv.add(0, 0, "dirt", 5);
        assertFalse(mt.scroll(inv, alone, false), "no other inventory");
        inv.add(2, 18, null, 0);
        assertFalse(mt.scroll(inv, none, false), "a slot outside the parts (armor, crafting in the player's inventory)");
        assertFalse(mt.scroll(inv, null, false));
        inv.cursor.set("dirt", 1);
        assertFalse(mt.scroll(inv, alone, false), "never with an item on the cursor");
        inv.cursor.set(null, 0);
        ((BoolOption) mt.getOption("Scroll moves items")).set(false);
        assertFalse(mt.scroll(inv, alone, false));
        mt.setEnabled(false);
        assertFalse(mt.press(inv, RIGHT, alone));
        assertTrue(inv.clicks.isEmpty());
    }

    @Test
    void scrollDirectionsIncludingTowardTheOtherInventory() {
        MouseTweaksModule mt = module();
        DropdownOption direction = (DropdownOption) mt.getOption("Scroll direction");
        assertTrue(mt.pushes(false, true) && !mt.pushes(true, true), "default: down sends, up pulls");
        direction.setIndex(1);
        assertTrue(mt.pushes(true, false) && !mt.pushes(false, false), "inverted");
        direction.setIndex(2);
        assertTrue(mt.pushes(true, true), "up toward an inventory above sends");
        assertFalse(mt.pushes(true, false), "up away from an inventory below pulls");
        assertTrue(mt.pushes(false, false), "down toward an inventory below sends");
        assertFalse(mt.pushes(false, true), "down away from an inventory above pulls");
        Inv inv = new Inv();
        Slot chest = inv.add(2, 18, "dirt", 3), player = inv.add(1, 84, "dirt", 5);
        mt.scroll(inv, player, true); // the chest is above the player's slot
        assertEquals(4, chest.count);
        assertEquals(4, player.count);
    }
}
