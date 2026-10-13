package com.thelads.core.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.util.ClientPaths;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

public final class CrosshairDrawingStore {
    private static Runnable drawingChangedListener = null;

    private CrosshairDrawingStore() {}

    public static Path path() {
        return ClientPaths.getCrosshairDrawingPath();
    }

    public static CrosshairDrawing defaultDrawing() {
        CrosshairDrawing value = new CrosshairDrawing(17, 17);
        for (CrosshairDesign.Rect r : CrosshairDesign.geometry(0, 5, 5, 2, 1)) {
            for (int y = r.top(); y < r.bottom(); y++) {
                for (int x = r.left(); x < r.right(); x++) {
                    value.set(x + 8, y + 8, true);
                }
            }
        }
        return value;
    }

    public static CrosshairDrawing load() {
        return load(defaultDrawing());
    }

    public static CrosshairDrawing load(CrosshairDrawing fallback) {
        try {
            Path path = path();
            if (!Files.isRegularFile(path) || Files.size(path) > 16384) {
                return fallback;
            }
            JsonObject data = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            List<String> rows = new ArrayList<>();
            if (data.has("rows") && data.get("rows").isJsonArray()) {
                for (var row : data.getAsJsonArray("rows")) {
                    rows.add(row.getAsString());
                }
            }
            int width = data.has("width") ? data.get("width").getAsInt() : 17;
            int height = data.has("height") ? data.get("height").getAsInt() : 17;
            return CrosshairDrawing.fromRows(width, height, rows);
        } catch (Exception failure) {
            LoggerFactory.getLogger("TheLadsCore").warn("Could not read crosshair drawing; using default", failure);
            return fallback;
        }
    }

    public static void save(CrosshairDrawing drawing) throws IOException {
        Path path = path();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        JsonObject json = new JsonObject();
        json.addProperty("width", drawing.width());
        json.addProperty("height", drawing.height());
        json.add("rows", new Gson().toJsonTree(drawing.rows()));
        Path parent = path.getParent() != null ? path.getParent() : Path.of(".");
        Path temp = Files.createTempFile(parent, "crosshair-", ".tmp");
        try {
            Files.writeString(temp, new Gson().toJson(json));
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void setDrawingChangedListener(Runnable listener) {
        drawingChangedListener = listener;
    }

    public static void notifyDrawingChanged() {
        if (drawingChangedListener != null) {
            try {
                drawingChangedListener.run();
            } catch (Throwable ignored) {}
        }
    }
}
