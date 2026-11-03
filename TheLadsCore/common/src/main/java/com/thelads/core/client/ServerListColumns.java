package com.thelads.core.client;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Side-by-side columns in the multiplayer list: the layout math each version's server list mixin shares, and the player's choice (an
 * option of the Draggable Server List module). A count is offered only while every column stays {@link #MIN_WIDTH} wide; a smaller window
 * or larger GUI scale falls back to what fits and the choice comes back when it fits again. Rows fill left to right, then down.
 */
public final class ServerListColumns {
    public static final String OPTION = "Columns";
    /** A vanilla row. */
    public static final int FULL_WIDTH = 305;
    /** The narrowest column offered: icon, a clipped name and version, player count and ping still read side by side without overlapping. */
    public static final int MIN_WIDTH = 200;
    public static final int MAX_COLUMNS = 3, GAP = 4;
    /** Kept free on each side of the columns, the scrollbar included. */
    private static final int MARGIN = 16;

    private ServerListColumns() {}

    /** A version's server list: lays its rows out in slots. AbstractSelectionList's own positioning ends in this. */
    public interface Layout {
        void lads$layout();
        int lads$columns();
        /** The dragged row: drawn as the ghost, not in its gap. */
        boolean lads$hidden(Object row);
    }

    /** A version's server list whose rows have no place of their own (1.20.1, 1.8.9): where it draws a row. */
    public interface Rows {
        int lads$rowLeft(int row);
        int lads$rowTop(int row);
    }

    /** 1.20.1's list answers the final row-only hit test while columns show. */
    public interface Hits {
        int lads$columns();
        Object lads$rowAt(double x, double y);
    }

    /** 1.8.9's list: the drag tells it the gap, as the drag runs in the screen; and how many rows it has, for the screen's arrow keys. */
    public interface Gap {
        void lads$gap(int from, int to);
        int lads$size();
    }

    /** One column of {@code columns} before it is capped at a vanilla row. */
    private static int raw(int columns, int listWidth) {
        return (listWidth - 2 * MARGIN - (columns - 1) * GAP) / columns;
    }

    /** The most columns a list this wide can show. */
    public static int most(int listWidth) {
        for (int columns = MAX_COLUMNS; columns > 1; columns--) if (raw(columns, listWidth) >= MIN_WIDTH) return columns;
        return 1;
    }

    /** What the list shows for a choice: the choice, or the most that fit. */
    public static int fit(int wanted, int listWidth) {
        return Math.max(1, Math.min(wanted, most(listWidth)));
    }

    public static int width(int columns, int listWidth) {
        return columns <= 1 ? FULL_WIDTH : Math.min(FULL_WIDTH, raw(columns, listWidth));
    }

    /** All columns and the gaps between them. */
    public static int total(int columns, int listWidth) {
        return columns * width(columns, listWidth) + (columns - 1) * GAP;
    }

    public static int rows(int count, int columns) {
        return (count + columns - 1) / columns;
    }

    public static int x(int slot, int columns, int left, int columnWidth) {
        return left + slot % columns * (columnWidth + GAP);
    }

    public static int y(int slot, int columns, int top, int rowHeight) {
        return top + slot / columns * rowHeight;
    }

    /**
     * The slot under a point, -1 when it is off the grid or off the list's rows ({@code count} of them; the last row need not be full). A
     * point in the gap between two columns counts as the column on its left.
     */
    public static int slotAt(double px, double py, int left, int top, int columnWidth, int rowHeight, int columns, int count) {
        if (px < left || py < top) return -1;
        int column = (int) ((px - left) / (columnWidth + GAP));
        if (column >= columns) return -1;
        int slot = (int) ((py - top) / rowHeight) * columns + column;
        return slot < count ? slot : -1;
    }

    /**
     * Arrow keys in columns: the row beside or above/below, -1 past an edge. Down from a row with nothing under it (a last row that is not
     * full) goes to the very last row.
     */
    public static int next(int index, int count, int columns, int dx, int dy) {
        if (dx != 0) {
            int to = index + dx, column = index % columns + dx;
            return column < 0 || column >= columns || to >= count ? -1 : to;
        }
        int to = index + dy * columns;
        if (to < 0) return -1;
        if (to < count) return to;
        return index / columns < (count - 1) / columns ? count - 1 : -1;
    }

    /** The next count the control offers after {@code current}; 1 after the last that fits. */
    public static int cycled(int current, int listWidth) {
        return current >= most(listWidth) ? 1 : current + 1;
    }

    private static SliderOption option() {
        Module module = ModuleManager.getInstance().getModule(ServerListDrag.NAME);
        return module != null && module.getOption(OPTION) instanceof SliderOption slider ? slider : null;
    }

    /** The saved choice, 1 to {@link #MAX_COLUMNS}. Read while the list lays out: a hash lookup. */
    public static int wanted() {
        SliderOption option = option();
        return option == null ? 1 : option.getIntValue();
    }

    /** The control's press: the next count that fits, saved. Returns it. */
    public static int cycle(int listWidth) {
        int next = cycled(fit(wanted(), listWidth), listWidth);
        SliderOption option = option();
        if (option != null) {
            option.setValue(next);
            ConfigManager.save();
        }
        return next;
    }
}
