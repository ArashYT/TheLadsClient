package com.thelads.core.client.title;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The pause menu on every version, in groups instead of one grid: Back to Game alone at the top; Advancements | Statistics;
 * Options | Lads Client; Multiplayer | the world's options (Open to LAN); Replays | Extras, then other mods' buttons in pairs;
 * and Save and Quit (or Disconnect) alone at the bottom, set apart. A narrow screen gets one column.
 */
public final class PauseMenuLayout {
    public enum Slot { BACK, ADVANCEMENTS, STATS, OPTIONS, LADS, MULTIPLAYER, WORLD, REPLAYS, EXTRAS, OTHER, QUIT }
    public record Box(int x, int y, int width, int height) {}

    private static final Slot[][] PAIRS = {{Slot.ADVANCEMENTS, Slot.STATS}, {Slot.OPTIONS, Slot.LADS}, {Slot.MULTIPLAYER, Slot.WORLD},
        {Slot.REPLAYS, Slot.EXTRAS}};
    static final int GAP = 4, APART = 10, MAX_WIDTH = 320;

    private PauseMenuLayout() {}

    /** Each button's icon (TitleScreenTheme), drawn before its label. */
    public static String icon(Slot slot) {
        return switch (slot) {
            case BACK -> "play";
            case ADVANCEMENTS -> "advancements";
            case STATS -> "stats";
            case OPTIONS -> "settings";
            case LADS -> "lads";
            case MULTIPLAYER -> "server";
            case WORLD -> "host";
            case REPLAYS -> "replay";
            case EXTRAS -> "dots";
            case OTHER -> "mods";
            case QUIT -> "quit";
        };
    }

    /** One box per entry of {@code slots} (same order), centred between {@code top} and {@code bottom}. */
    public static List<Box> arrange(List<Slot> slots, int width, int top, int bottom) {
        List<int[]> rows = new ArrayList<>();
        boolean[] placed = new boolean[slots.size()];
        row(rows, placed, slots, Slot.BACK);
        for (Slot[] pair : PAIRS) row(rows, placed, slots, pair);
        List<Integer> rest = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) if (!placed[i] && slots.get(i) != Slot.QUIT) rest.add(i);
        for (int i = 0; i < rest.size(); i += 2)
            rows.add(i + 1 < rest.size() ? new int[] {rest.get(i), rest.get(i + 1)} : new int[] {rest.get(i)});
        int quitRows = rows.size();
        for (int i = 0; i < slots.size(); i++) if (slots.get(i) == Slot.QUIT) rows.add(new int[] {i});
        if (width < 380) { // one column: every button on its own row
            List<int[]> single = new ArrayList<>();
            for (int[] row : rows) for (int index : row) single.add(new int[] {index});
            quitRows = 0;
            for (int[] row : single) if (slots.get(row[0]) != Slot.QUIT) quitRows++;
            rows = single;
        }
        int apart = quitRows < rows.size() && quitRows > 0 ? APART : 0;
        int height = Math.max(17, Math.min(22, (bottom - top - apart - GAP * (rows.size() - 1)) / Math.max(1, rows.size())));
        int block = rows.size() * height + GAP * (rows.size() - 1) + apart;
        int total = Math.min(width - 32, MAX_WIDTH), left = (width - total) / 2, half = (total - GAP) / 2;
        int y = top + Math.max(0, (bottom - top - block) / 2);
        Box[] boxes = new Box[slots.size()];
        for (int r = 0; r < rows.size(); r++) {
            if (r == quitRows && r > 0) y += apart;
            int[] row = rows.get(r);
            if (row.length == 1) boxes[row[0]] = new Box(left, y, total, height);
            else {
                boxes[row[0]] = new Box(left, y, half, height);
                boxes[row[1]] = new Box(left + total - half, y, half, height);
            }
            y += height + GAP;
        }
        return Arrays.asList(boxes);
    }

    /** The first not-yet-placed entry of each group slot present, as one row (alone when its partner is missing). */
    private static void row(List<int[]> rows, boolean[] placed, List<Slot> slots, Slot... group) {
        List<Integer> row = new ArrayList<>();
        for (Slot slot : group)
            for (int i = 0; i < slots.size(); i++)
                if (!placed[i] && slots.get(i) == slot) { placed[i] = true; row.add(i); break; }
        if (!row.isEmpty()) rows.add(row.stream().mapToInt(Integer::intValue).toArray());
    }
}
