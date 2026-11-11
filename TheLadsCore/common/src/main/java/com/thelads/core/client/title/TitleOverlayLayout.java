package com.thelads.core.client.title;

import com.thelads.core.client.title.TitleScreenTheme.Rect;

/**
 * Pure layout math of the title screen's two popups: the Panorama dropdown and the chat sidebar with its edge tab. Canvas pixels,
 * no drawing and no game state, so tests can check every canvas size down to the 240x180 minimum.
 */
public final class TitleOverlayLayout {
    private TitleOverlayLayout() {}

    public static final int MIN_WIDTH = 240, MIN_HEIGHT = 180;

    // --- Panorama dropdown ---
    public static final int ROW = 24, LIST_PAD = 4, GAP = 4, EDGE = 6, MAX_DROPDOWN_WIDTH = 236;

    /** The dropdown's panel, the list area inside it, the full height of all rows and how far the list scrolls. */
    public record Dropdown(Rect panel, Rect list, int contentHeight, int maxScroll) {}

    /**
     * The dropdown for {@code rows} rows below the anchor (the Panorama button), its right edge on the button's: at least EDGE from
     * every screen edge, as tall as the rows need and no taller than the room below the button (it then scrolls).
     */
    public static Dropdown dropdown(int sw, int sh, Rect anchor, int rows) {
        sw = Math.max(MIN_WIDTH, sw);
        sh = Math.max(MIN_HEIGHT, sh);
        int width = Math.min(MAX_DROPDOWN_WIDTH, sw - 2 * EDGE);
        int x = Math.max(EDGE, Math.min(anchor.x() + anchor.width() - width, sw - width - EDGE));
        int y = anchor.y() + anchor.height() + GAP;
        int content = Math.max(1, rows) * ROW;
        int room = sh - y - EDGE;
        int height = Math.max(ROW + 2 * LIST_PAD, Math.min(content + 2 * LIST_PAD, room));
        height = Math.min(height, sh - 2 * EDGE);
        y = Math.min(y, sh - EDGE - height);
        Rect panel = new Rect(x, y, width, height);
        Rect list = new Rect(x + LIST_PAD, y + LIST_PAD, width - 2 * LIST_PAD, height - 2 * LIST_PAD);
        return new Dropdown(panel, list, content, Math.max(0, content - list.height()));
    }

    // --- Chat sidebar ---
    public static final int TAB_WIDTH = 14, TAB_HEIGHT = 40, HEADER = 26, SEGMENT_HEIGHT = 20, SEARCH_HEIGHT = 20, FOOTER = 22,
        SIDEBAR_PAD = 8, PERSON_ROW = 34, SECTION_ROW = 16, MAX_SIDEBAR_WIDTH = 264;

    /** The sidebar's width: 264, or what a small canvas leaves beside the 40 it keeps clear. */
    public static int sidebarWidth(int sw) {
        return Math.max(160, Math.min(MAX_SIDEBAR_WIDTH, Math.max(MIN_WIDTH, sw) - 40));
    }

    /** Ease-out, 0 to 1. */
    public static float ease(float progress) {
        float t = Math.max(0f, Math.min(1f, progress));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** The sidebar's left edge while it is progress (0 closed, 1 open) of the way out: sw when closed. */
    public static int sidebarX(int sw, float progress) {
        sw = Math.max(MIN_WIDTH, sw);
        return sw - Math.round(sidebarWidth(sw) * ease(progress));
    }

    /** The edge tab with the chat bubble: flush to the right edge, or to the sidebar's left edge while it is out; centred on the height. */
    public static Rect tab(int sw, int sh, float progress) {
        sh = Math.max(MIN_HEIGHT, sh);
        return new Rect(sidebarX(sw, progress) - TAB_WIDTH, (sh - TAB_HEIGHT) / 2, TAB_WIDTH, TAB_HEIGHT);
    }

    /** The sidebar's parts at a given x: panel, the two segments, the search box, the scrolling list and the footer button. */
    public record Sidebar(Rect panel, Rect messages, Rect friends, Rect search, Rect list, Rect footer, Rect close) {}

    public static Sidebar sidebar(int sw, int sh, float progress) {
        sw = Math.max(MIN_WIDTH, sw);
        sh = Math.max(MIN_HEIGHT, sh);
        int x = sidebarX(sw, progress), w = sidebarWidth(sw), inner = w - 2 * SIDEBAR_PAD, left = x + SIDEBAR_PAD;
        int segments = HEADER + 2, search = segments + SEGMENT_HEIGHT + 6, list = search + SEARCH_HEIGHT + 6;
        int half = (inner - 2) / 2;
        return new Sidebar(new Rect(x, 0, w, sh),
            new Rect(left, segments, half, SEGMENT_HEIGHT),
            new Rect(left + half + 2, segments, inner - half - 2, SEGMENT_HEIGHT),
            new Rect(left, search, inner, SEARCH_HEIGHT),
            new Rect(x, list, w, sh - list - FOOTER - 6),
            new Rect(left, sh - FOOTER - 4, inner, FOOTER),
            new Rect(x + w - SIDEBAR_PAD - 14, 6, 14, 14));
    }

    // --- Scrolling lists ---
    public static boolean inside(Rect r, double x, double y) {
        return x >= r.x() && y >= r.y() && x < r.x() + r.width() && y < r.y() + r.height();
    }

    public static int clampScroll(int scroll, int contentHeight, int viewHeight) {
        return Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewHeight)));
    }

    public static int sum(int[] heights) {
        int total = 0;
        for (int h : heights) total += h;
        return total;
    }

    /** The row under a point relY below the list's top when scrolled by scroll, or -1 (past the last row or above the first). */
    public static int rowAt(int[] heights, int scroll, int relY) {
        int y = relY + scroll;
        if (y < 0) return -1;
        int top = 0;
        for (int i = 0; i < heights.length; i++) {
            if (y < top + heights[i]) return i;
            top += heights[i];
        }
        return -1;
    }

    /** The scroll that brings row index fully into a view viewHeight tall, the least change from scroll. */
    public static int scrollToShow(int[] heights, int index, int scroll, int viewHeight) {
        if (index < 0 || index >= heights.length) return scroll;
        int top = 0;
        for (int i = 0; i < index; i++) top += heights[i];
        int bottom = top + heights[index];
        if (top < scroll) return top;
        if (bottom > scroll + viewHeight) return bottom - viewHeight;
        return scroll;
    }
}
