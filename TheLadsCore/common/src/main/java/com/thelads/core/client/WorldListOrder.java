package com.thelads.core.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.util.ClientPaths;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Persistent custom ordering for the Singleplayer world selection list.
 * Order is stored in {@code thelads_world_order.json} inside Lads' shared config directory (never touching {@code level.dat}).
 * Worlds not yet present in the order file appear at the top, sorted by last played descending as vanilla does.
 */
public final class WorldListOrder {
    public static final String FILE_NAME = "thelads_world_order.json";

    private WorldListOrder() {}

    /** Drag reordering is enabled when Draggable Server List is enabled and no search filter is narrowing the list. */
    public static boolean canDrag(String filter) {
        return ServerListDrag.enabled() && (filter == null || filter.trim().isEmpty());
    }

    @SuppressWarnings("deprecation")
    public static List<String> parseOrder(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JsonElement root = new JsonParser().parse(json);
            JsonArray arr = null;
            if (root != null && root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("worlds") && obj.get("worlds").isJsonArray()) {
                    arr = obj.getAsJsonArray("worlds");
                }
            } else if (root != null && root.isJsonArray()) {
                arr = root.getAsJsonArray();
            }
            if (arr == null) {
                return Collections.emptyList();
            }
            Set<String> seen = new LinkedHashSet<String>();
            for (JsonElement el : arr) {
                if (el != null && el.isJsonPrimitive()) {
                    String id = el.getAsString();
                    if (id != null && !id.trim().isEmpty()) {
                        seen.add(id);
                    }
                }
            }
            return new ArrayList<String>(seen);
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    public static String serializeOrder(List<String> worldIds) {
        return serializeOrder(worldIds, Collections.<String>emptyList());
    }

    public static String serializeOrder(List<String> worldIds, List<String> pinned) {
        JsonArray arr = new JsonArray();
        if (worldIds != null) {
            Set<String> seen = new LinkedHashSet<String>();
            for (String id : worldIds) {
                if (id != null && !id.trim().isEmpty() && seen.add(id)) {
                    arr.add(id);
                }
            }
        }
        JsonObject obj = new JsonObject();
        obj.add("worlds", arr);
        if (pinned != null && !pinned.isEmpty()) {
            JsonArray pins = new JsonArray();
            for (String id : new LinkedHashSet<String>(pinned)) {
                if (id != null && !id.trim().isEmpty()) pins.add(id);
            }
            obj.add("pinned", pins);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(obj);
    }

    /** World ids pinned to the top of the list ("pinned" next to "worlds" in the same file). */
    @SuppressWarnings("deprecation")
    public static List<String> parsePinned(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            JsonElement root = new JsonParser().parse(json);
            if (root == null || !root.isJsonObject() || !root.getAsJsonObject().has("pinned")
                    || !root.getAsJsonObject().get("pinned").isJsonArray()) {
                return Collections.emptyList();
            }
            Set<String> seen = new LinkedHashSet<String>();
            for (JsonElement el : root.getAsJsonObject().getAsJsonArray("pinned")) {
                if (el != null && el.isJsonPrimitive() && !el.getAsString().trim().isEmpty()) seen.add(el.getAsString());
            }
            return new ArrayList<String>(seen);
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    public static List<String> loadPinned() {
        File file = ClientPaths.getWorldOrderFile();
        if (file == null || !file.isFile()) return Collections.emptyList();
        try {
            return parsePinned(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    public static boolean isPinned(String worldId) {
        return worldId != null && loadPinned().contains(worldId);
    }

    /** Pins the world (or unpins it when it is pinned); returns whether it is pinned now. */
    public static boolean togglePin(String worldId) {
        List<String> pins = new ArrayList<String>(loadPinned());
        boolean now = !pins.remove(worldId);
        if (now) pins.add(worldId);
        save(loadOrder(), pins);
        return now;
    }

    /** Pinned worlds first (in their current order), then the rest. */
    public static <T> List<T> pinnedFirst(List<T> worlds, List<String> pinned, Function<T, String> idExtractor) {
        if (pinned == null || pinned.isEmpty()) return worlds;
        List<T> top = new ArrayList<T>(), rest = new ArrayList<T>();
        for (T w : worlds) (pinned.contains(idExtractor.apply(w)) ? top : rest).add(w);
        top.addAll(rest);
        return top;
    }

    public static List<String> loadOrder() {
        File file = ClientPaths.getWorldOrderFile();
        if (file == null || !file.isFile()) {
            return Collections.emptyList();
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return parseOrder(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    public static void saveOrder(List<String> worldIds) {
        save(worldIds, loadPinned());
    }

    private static void save(List<String> worldIds, List<String> pinned) {
        byte[] payload = serializeOrder(worldIds, pinned).getBytes(StandardCharsets.UTF_8);
        Set<Path> written = new LinkedHashSet<Path>();
        File primary = ClientPaths.getWorldOrderFile();
        if (primary != null) {
            writeBytes(primary.toPath(), payload);
            written.add(primary.toPath().toAbsolutePath().normalize());
        }
        for (File mirror : ClientPaths.getMirrorTargetsForSharedFile(FILE_NAME)) {
            Path p = mirror.toPath().toAbsolutePath().normalize();
            if (written.add(p)) {
                writeBytes(p, payload);
            }
        }
    }

    private static void writeBytes(Path path, byte[] payload) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, payload);
        } catch (IOException ignored) {}
    }

    /**
     * Sorts {@code worlds}: worlds missing from {@code savedOrder} appear first sorted by {@code lastPlayed} descending
     * (matching vanilla's default order for newly created or discovered worlds), followed by worlds in {@code savedOrder}
     * in their saved sequence.
     */
    public static <T> List<T> sort(
            List<T> worlds,
            List<String> savedOrder,
            final Function<T, String> idExtractor,
            final ToLongFunction<T> lastPlayedExtractor) {
        if (worlds == null || worlds.isEmpty()) {
            return new ArrayList<T>();
        }
        final Map<String, Integer> rank = new HashMap<String, Integer>();
        if (savedOrder != null) {
            for (int i = 0; i < savedOrder.size(); i++) {
                String id = savedOrder.get(i);
                if (id != null && !rank.containsKey(id)) {
                    rank.put(id, Integer.valueOf(i));
                }
            }
        }
        List<T> unsaved = new ArrayList<T>();
        List<T> saved = new ArrayList<T>();
        for (T world : worlds) {
            if (world == null) continue;
            String id = idExtractor.apply(world);
            if (id != null && rank.containsKey(id)) {
                saved.add(world);
            } else {
                unsaved.add(world);
            }
        }
        Collections.sort(unsaved, new Comparator<T>() {
            @Override
            public int compare(T a, T b) {
                return Long.compare(lastPlayedExtractor.applyAsLong(b), lastPlayedExtractor.applyAsLong(a));
            }
        });
        Collections.sort(saved, new Comparator<T>() {
            @Override
            public int compare(T a, T b) {
                return Integer.compare(
                        rank.get(idExtractor.apply(a)).intValue(),
                        rank.get(idExtractor.apply(b)).intValue());
            }
        });
        List<T> out = new ArrayList<T>(unsaved.size() + saved.size());
        out.addAll(unsaved);
        out.addAll(saved);
        return out;
    }

    public static <T> List<T> sortedCopy(
            List<T> worlds,
            Function<T, String> idExtractor,
            ToLongFunction<T> lastPlayedExtractor) {
        return pinnedFirst(sort(worlds, loadOrder(), idExtractor, lastPlayedExtractor), loadPinned(), idExtractor);
    }

    /** Draws with the caller's fill, so every version's mixin shares the pin glyph. */
    public interface Fill { void fill(int x1, int y1, int x2, int y2, int argb); }

    public static final int PIN_SIZE = 12;

    public static void drawPin(Fill f, int x, int y, boolean pinned, boolean hover) {
        int c = pinned ? 0xFFFFC83D : hover ? 0xFFE0E0E0 : 0x88B0B0B0;
        f.fill(x + 3, y + 1, x + 9, y + 2, c);   // head
        f.fill(x + 4, y + 2, x + 8, y + 6, c);   // body
        f.fill(x + 2, y + 6, x + 10, y + 7, c);  // brim
        f.fill(x + 5, y + 7, x + 7, y + 11, c);  // needle
    }

    /** Down from the row's top: the bottom-right corner, under the play time and size the top two lines show on the right. */
    private static final int PIN_TOP = 21;

    public static boolean pinHit(int rowX, int rowY, int rowW, double mx, double my) {
        int x = rowX + rowW - PIN_SIZE - 3, y = rowY + PIN_TOP;
        return mx >= x && mx < x + PIN_SIZE && my >= y && my < y + PIN_SIZE;
    }

    /** A pin on a world row: always for a pinned world, on hover for the rest. */
    public static void drawPinFor(Fill f, int rowX, int rowY, int rowW, String worldId, boolean rowHover, double mx, double my) {
        boolean pinned = isPinned(worldId);
        if (!pinned && !rowHover) return;
        drawPin(f, rowX + rowW - PIN_SIZE - 3, rowY + PIN_TOP, pinned, pinHit(rowX, rowY, rowW, mx, my));
    }
}
