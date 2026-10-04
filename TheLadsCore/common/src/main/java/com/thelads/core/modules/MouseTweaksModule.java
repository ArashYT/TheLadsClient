package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import java.util.ArrayList;
import java.util.List;

/**
 * Mouse Tweaks, written for Lads: drags and the scroll wheel in container screens. Every change is an ordinary slot click that
 * the adapter sends through the screen (the server checks each one), with the cursor carrying items as a player would by hand.
 * The adapters report press, drag, release and scroll; true from {@link #press}, {@link #release} or {@link #scroll} means the
 * tweak took that event and the screen must not handle it as well.
 */
public class MouseTweaksModule extends Module {
    public static final String NAME = "MouseTweaks";
    public static final int LEFT = 0, RIGHT = 1;

    /** One container screen as the adapter sees it: {@code S} a slot, {@code I} an item stack (empty counts 0). */
    public interface Menu<S, I> {
        /** The screen's usable slots, in menu order. */
        List<S> slots();
        I item(S slot);
        I carried();
        int count(I item);
        /** Same item and data, both non-empty. */
        boolean same(I a, I b);
        boolean mayPlace(S slot, I item);
        /** The most of {@code item} the slot holds (the smaller of the slot's and the item's own limit). */
        int limit(S slot, I item);
        /** Inventory part for the wheel: 0 takes no part; slots of any other non-zero part are "the other inventory". */
        int part(S slot);
        /** Screen y of the slot, for the position-aware wheel. */
        int y(S slot);
        /** An ordinary click, LEFT or RIGHT, or a shift-click (quick move). */
        void click(S slot, int button, boolean quickMove);
    }

    private final BoolOption rightDrag, leftDrag, shiftDrag, wheel;
    private final DropdownOption searchOrder, direction;
    private int dragButton = -1;
    private Object lastSlot;

    public MouseTweaksModule() {
        super(NAME, "Right-drag puts one item in each slot, left-drag gathers matching items, shift-drag moves items, the wheel moves them one by one.");
        rightDrag = addOption(new BoolOption("Right-drag places items", true));
        leftDrag = addOption(new BoolOption("Left-drag gathers items", true));
        shiftDrag = addOption(new BoolOption("Shift-drag moves items", true));
        wheel = addOption(new BoolOption("Scroll moves items", true));
        searchOrder = addOption(new DropdownOption("Scroll pulls from", 1, "First slot first", "Last slot first"));
        direction = addOption(new DropdownOption("Scroll direction", 0, "Down sends, up pulls", "Up sends, down pulls", "Toward the other inventory"));
        setEnabled(true);
    }

    /** A button went down over {@code slot} (null: none). True: the tweak clicked, so the screen must skip this press. */
    public <S, I> boolean press(Menu<S, I> menu, int button, S slot) {
        dragButton = -1;
        lastSlot = null;
        if (!isEnabled() || slot == null) return false;
        boolean carrying = menu.count(menu.carried()) > 0;
        if (button == RIGHT && rightDrag.get() && carrying) {
            // Replaces vanilla's even split: this slot gets one item now (an ordinary right click), every slot entered one more.
            menu.click(slot, RIGHT, false);
        } else if (button != LEFT || carrying || !leftDrag.get() && !shiftDrag.get()) {
            return false; // a left press with an item keeps vanilla's drag-splitting
        }
        dragButton = button;
        lastSlot = slot;
        return button == RIGHT; // the screen still clicks the slot of a left press
    }

    /** The mouse moved with {@code button} held, now over {@code slot} (null: none); each slot counts once per visit. */
    public <S, I> void drag(Menu<S, I> menu, int button, S slot, boolean shift) {
        if (button != dragButton || slot == lastSlot) return;
        lastSlot = slot;
        if (slot == null || !isEnabled()) return;
        I carried = menu.carried(), item = menu.item(slot);
        int carriedCount = menu.count(carried), count = menu.count(item);
        if (button == RIGHT) {
            // Only stackable items: a right click with a single item can mean more (a bundle drops one of its contents).
            if (carriedCount > 0 && menu.limit(slot, carried) > 1 && fits(menu, slot, carried, 1)) menu.click(slot, RIGHT, false);
        } else if (carriedCount == 0) {
            if (shift && shiftDrag.get() && count > 0) menu.click(slot, LEFT, true);
        } else if (leftDrag.get() && menu.same(item, carried)) {
            if (shift) {
                menu.click(slot, LEFT, true); // matching items move to the other inventory; the cursor keeps its stack
            } else if (!menu.mayPlace(slot, carried)) {
                menu.click(slot, LEFT, false); // an output slot: a click takes what fits onto the cursor
            } else if (carriedCount + count <= menu.limit(slot, carried)) {
                // A click adds the cursor to the matching stack; a second click takes the merged stack back.
                // ponytail: skips a stack that would overflow the cursor; a partial take would need a free slot to park in.
                menu.click(slot, LEFT, false);
                menu.click(slot, LEFT, false);
            }
        }
    }

    /** A button went up. True: the tweak owned that press, so the screen must not click again on release. */
    public boolean release(int button) {
        if (button != dragButton) return false;
        dragButton = -1;
        lastSlot = null;
        return button == RIGHT;
    }

    /** A wheel turn over {@code slot}; {@code up} is away from the user. True: the tweak took it. */
    public <S, I> boolean scroll(Menu<S, I> menu, S slot, boolean up) {
        if (!isEnabled() || !wheel.get() || slot == null || menu.count(menu.carried()) > 0) return false;
        I item = menu.item(slot);
        int part = menu.part(slot);
        if (menu.count(item) == 0 || part == 0) return false;
        List<S> others = new ArrayList<>();
        long ySum = 0;
        for (S other : menu.slots()) {
            int otherPart = menu.part(other);
            if (otherPart != 0 && otherPart != part) { others.add(other); ySum += menu.y(other); }
        }
        if (others.isEmpty()) return false;
        if (pushes(up, ySum < (long) menu.y(slot) * others.size())) push(menu, slot, item, others);
        else pull(menu, slot, item, others);
        return true;
    }

    /** Whether this wheel turn sends items to the other inventory (otherwise it pulls them in). */
    boolean pushes(boolean up, boolean otherAbove) {
        return switch (direction.getIndex()) {
            case 1 -> up;
            case 2 -> up == otherAbove; // scrolling toward the other inventory sends, away from it pulls
            default -> !up;
        };
    }

    /** One item from {@code slot} to the other inventory: onto a matching stack with room first, else the first free slot. */
    private <S, I> void push(Menu<S, I> menu, S slot, I item, List<S> others) {
        boolean output = !menu.mayPlace(slot, item);
        int amount = output ? menu.count(item) : 1; // an output slot gives all of it at once (one craft, smelt or trade)
        S target = null;
        for (S other : others)
            if (menu.count(menu.item(other)) > 0 && fits(menu, other, item, amount)) { target = other; break; }
        for (int i = 0; target == null && i < others.size(); i++)
            if (fits(menu, others.get(i), item, amount)) target = others.get(i);
        if (target == null) return;
        menu.click(slot, LEFT, false);
        if (menu.count(menu.carried()) == 0) return; // the slot refused the pickup
        menu.click(target, output ? LEFT : RIGHT, false);
        if (!output && menu.count(menu.carried()) > 0) menu.click(slot, LEFT, false);
    }

    /** One matching item from the other inventory into {@code slot}, searched in the chosen order. */
    private <S, I> void pull(Menu<S, I> menu, S slot, I item, List<S> others) {
        if (!menu.mayPlace(slot, item) || menu.count(item) >= menu.limit(slot, item)) return;
        boolean lastFirst = searchOrder.getIndex() == 1;
        for (int i = 0; i < others.size(); i++) {
            S source = others.get(lastFirst ? others.size() - 1 - i : i);
            I sourceItem = menu.item(source);
            if (!menu.same(sourceItem, item) || !menu.mayPlace(source, sourceItem)) continue; // the rest must go back
            menu.click(source, LEFT, false);
            if (menu.count(menu.carried()) == 0) return;
            menu.click(slot, RIGHT, false);
            if (menu.count(menu.carried()) > 0) menu.click(source, LEFT, false);
            return;
        }
    }

    /** {@code slot} takes {@code amount} more of {@code item}: allowed there, empty or matching, within its limit. */
    private static <S, I> boolean fits(Menu<S, I> menu, S slot, I item, int amount) {
        I there = menu.item(slot);
        int count = menu.count(there);
        return menu.mayPlace(slot, item) && (count == 0 || menu.same(there, item)) && count + amount <= menu.limit(slot, item);
    }
}
