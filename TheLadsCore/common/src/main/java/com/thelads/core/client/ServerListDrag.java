package com.thelads.core.client;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.function.IntFunction;

/**
 * Draggable Server List (Feather): hold a saved server in the multiplayer list and drag it; the real row follows the mouse as a slightly
 * translucent ghost and the others open a gap where it lands. servers.dat keeps the new order. A press only becomes a drag after the mouse
 * moved {@link #THRESHOLD} pixels, so clicks, double-click to join and vanilla's move arrows work as before. The adapters' server list
 * mixins track the drag; this class holds the rules.
 */
public final class ServerListDrag {
    public static final String NAME = "Draggable Server List";
    public static final int THRESHOLD = 4;
    /** Near the list's top or bottom edge (pixels) a drag scrolls the list, this many pixels a frame. */
    public static final int SCROLL_EDGE = 16, SCROLL_STEP = 3;
    /** How opaque the dragged row's ghost is, 0 to 255. */
    public static final int GHOST_ALPHA = 0xC0;
    /** Behind the ghost, so it reads over the rows under it; and in the gap it will land in. */
    public static final int GHOST_BACK = 0x70000000, GAP_FILL = 0x18FFFFFF;
    private static Module module;
    private static boolean ghost;
    /** QA: synthetic drags have no real button down, which the versions that watch the button state would read as a release. */
    public static boolean heldForQa;
    /** QA: where the screens draw the mouse (hover states) with the player's real mouse left alone; -1: the real one. */
    public static int qaMouseX = -1, qaMouseY = -1;

    private ServerListDrag() {}

    /** Asked when a press starts, not per frame. */
    public static boolean enabled() {
        if (module == null) module = ModuleManager.getInstance().getModule(NAME);
        return module != null && module.isEnabled();
    }

    public static boolean dragging(double pressY, double y) { return Math.abs(y - pressY) > THRESHOLD; }

    /** {@link #dragging(double, double)} in both directions: in columns a row also drags sideways. */
    public static boolean dragging(double pressX, double pressY, double x, double y) {
        return Math.abs(x - pressX) > THRESHOLD || dragging(pressY, y);
    }

    /** The saved-server row a drag drops on: the row under the mouse (the LAN rows below count as the last saved one), else where it was. */
    public static int target(int hovered, int previous, int count) {
        return target(hovered, previous, count, 0);
    }

    /** {@link #target(int, int, int)}, never above {@code first}: 1 while The Lads Network is pinned at 0, so nothing lands above it. */
    public static int target(int hovered, int previous, int count, int first) {
        return hovered < 0 ? previous : Math.max(first, Math.min(hovered, count - 1));
    }

    /** 1 when the list's first row is the pinned network: the first row that may move. */
    public static int firstMovable(String firstAddress) {
        return firstAddress != null && LadsNetwork.isPinned(firstAddress) ? 1 : 0;
    }

    /** Vanilla's arrows and Shift+arrows swap two rows: never with the pinned first row. */
    public static boolean canSwap(int a, int b, int first) {
        return a >= first && b >= first;
    }

    /** The slot a row shows in while the row {@code from} is dragged to {@code to}: the rows between close up, leaving the gap at {@code to}. */
    public static int slot(int row, int from, int to) {
        if (from < 0 || to < 0) return row;
        if (row == from) return to;
        if (from < row && row <= to) return row - 1;
        if (to <= row && row < from) return row + 1;
        return row;
    }

    /** Set around drawing the ghost; the version's graphics mixin then runs every colour through {@link #ghostColor}. */
    public static void ghost(boolean on) { ghost = on; }

    public static boolean ghost() { return ghost; }

    public static int ghostColor(int argb) {
        return ghost ? ((argb >>> 24) * GHOST_ALPHA / 255) << 24 | argb & 0xFFFFFF : argb;
    }

    /** Sets index {@code i} to {@code value} (ServerList.replace, which does not save). */
    public interface Put<T> {
        void put(int i, T value);
    }

    /** Moves the entry at {@code from} to {@code to}, shifting the ones between by one, as dragging a row does. */
    public static <T> void move(int from, int to, IntFunction<T> get, Put<T> put) {
        if (from == to) return;
        T moving = get.apply(from);
        int step = from < to ? 1 : -1;
        for (int i = from; i != to; i += step) put.put(i, get.apply(i + step));
        put.put(to, moving);
    }
}
