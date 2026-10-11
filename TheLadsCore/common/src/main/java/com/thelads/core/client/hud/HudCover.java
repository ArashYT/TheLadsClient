package com.thelads.core.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * What covers the Lads HUD (1.9.0): the tab list's box while it shows, and the debug screen's text boxes while F3 is open, in
 * vanilla GUI units. A widget under one is not drawn (HudManager, and the Lads overlays drawn outside it), so the tab list and the
 * debug text read over a clear background. Each version adds the boxes as it draws them (the tab list from its own fills, the debug
 * text line by line) and starts a frame ({@link #frame}) before its HUD draws.
 * <p>
 * The tab list is drawn before the Lads HUD on every version (on 26.x inside the same capped HUD build, whose replay adds its box
 * again), so it covers from what this frame drew. 26.x draws the debug screen after the HUD: its boxes are then the last frame's,
 * while F3 is still open.
 */
public final class HudCover {
    public static final int TAB = 0, DEBUG = 1;
    /** Each box as {source, x1, y1, x2, y2}: what this frame drew so far, and what the last frame drew. */
    private static List<int[]> now = new ArrayList<>(), last = new ArrayList<>();

    private HudCover() {}

    /** A new frame begins: what was drawn so far becomes the last frame's. */
    public static void frame() {
        List<int[]> previous = last;
        last = now;
        now = previous;
        now.clear();
    }

    /** A box drawn this frame over the HUD's place (any corner order). */
    public static void add(int source, double x1, double y1, double x2, double y2) {
        int left = (int) Math.floor(Math.min(x1, x2)), top = (int) Math.floor(Math.min(y1, y2));
        int right = (int) Math.ceil(Math.max(x1, x2)), bottom = (int) Math.ceil(Math.max(y1, y2));
        if (right > left && bottom > top) now.add(new int[] {source, left, top, right, bottom});
    }

    /**
     * One column of the game's own debug text, as the game draws it: from y 2, 9 a line, left or right aligned 2 from the edge,
     * each non-empty line on a box one pixel larger than its text.
     */
    public static void debugColumn(List<String> lines, boolean left, int screenWidth, ToIntFunction<String> width) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isEmpty()) continue;
            int w = width.applyAsInt(line), x = left ? 2 : screenWidth - 2 - w, y = 2 + 9 * i;
            add(DEBUG, x - 1, y - 1, x + w + 1, y + 8);
        }
    }

    /** Whether the box x1..x2, y1..y2 (GUI units) is under the tab list, or under the debug text while {@code debugOpen}. */
    public static boolean covered(double x1, double y1, double x2, double y2, boolean debugOpen) {
        boolean debugNow = false;
        for (int[] box : now) {
            if (box[0] == DEBUG) {
                debugNow = true;
                if (!debugOpen) continue;
            }
            if (overlaps(box, x1, y1, x2, y2)) return true;
        }
        if (debugOpen && !debugNow)
            for (int[] box : last) if (box[0] == DEBUG && overlaps(box, x1, y1, x2, y2)) return true;
        return false;
    }

    /**
     * For what a version draws before the tab list and the debug text in a frame (vanilla's boss bars, effects and sidebar, mods'
     * overlays): the last frame's boxes count too, so it goes on the frame after they open and comes back the frame after they close.
     */
    public static boolean coveredBefore(double x1, double y1, double x2, double y2) {
        boolean debugOpen = com.thelads.core.client.bridge.LadsGameBridge.get().isDebugScreenOpen();
        for (List<int[]> boxes : List.of(now, last))
            for (int[] box : boxes) if ((box[0] != DEBUG || debugOpen) && overlaps(box, x1, y1, x2, y2)) return true;
        return false;
    }

    /** Vanilla's boss bars: names from y 3 (1.8.9: 2), bars 182 wide at y 12, 19 apart. */
    public static boolean bossBarsCovered(int screenWidth, int bars) {
        return bars > 0 && coveredBefore(screenWidth / 2 - 91, 2, screenWidth / 2 + 91, 12 + 19 * (bars - 1) + 5);
    }

    /** Vanilla's effect icons in the top right: beneficial ones on the first row, the others on the second, 25 apart. */
    public static boolean effectsCovered(int screenWidth, int beneficial, int harmful) {
        int columns = Math.max(beneficial, harmful);
        return columns > 0 && coveredBefore(screenWidth - 25 * columns, 1, screenWidth, harmful > 0 ? 50 : 25);
    }

    /** Vanilla's sidebar: right aligned, its lines (9 high, under the title) centred a third of the way below the middle. */
    public static boolean sidebarCovered(int screenWidth, int screenHeight, ToIntFunction<String> width) {
        if (now.isEmpty() && last.isEmpty()) return false;
        var board = com.thelads.core.client.bridge.LadsGameBridge.get().getScoreboard();
        if (board == null) return false;
        int wide = width.applyAsInt(board.title()), separator = width.applyAsInt(": "), lines = Math.min(15, board.lines().size());
        for (var line : board.lines())
            wide = Math.max(wide, width.applyAsInt(line.name()) + (line.value().isEmpty() ? 0 : separator + width.applyAsInt(line.value())));
        int bottom = screenHeight / 2 + lines * 9 / 3;
        return coveredBefore(screenWidth - wide - 5, bottom - lines * 9 - 10, screenWidth - 1, bottom);
    }

    static boolean overlaps(int[] box, double x1, double y1, double x2, double y2) {
        return x1 < box[3] && box[1] < x2 && y1 < box[4] && box[2] < y2;
    }

    /** QA: the boxes this frame drew so far, as {source, x1, y1, x2, y2}. */
    public static List<int[]> boxes() {
        return new ArrayList<>(now);
    }
}
