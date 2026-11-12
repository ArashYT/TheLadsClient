package com.thelads.core.client.social;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thelads.core.client.util.ClientPaths;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** The player's best friends: a private list kept on this PC (thelads_best_friends.json next to the other shared config), not sent anywhere. */
public final class BestFriends {
    static final String FILE = "thelads_best_friends.json";
    private static final Gson GSON = new Gson();
    private static Set<UUID> ids;
    private static int version;

    private BestFriends() {}

    private static File file() {
        File order = ClientPaths.getWorldOrderFile();
        return order == null || order.getParentFile() == null ? null : new File(order.getParentFile(), FILE);
    }

    private static synchronized Set<UUID> ids() {
        if (ids == null) {
            ids = new LinkedHashSet<>();
            File f = file();
            if (f != null && f.isFile()) {
                try {
                    ids.addAll(parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
                } catch (IOException ignored) {}
            }
        }
        return ids;
    }

    static Set<UUID> parse(String json) {
        Set<UUID> out = new LinkedHashSet<>();
        try {
            JsonObject o = GSON.fromJson(json, JsonObject.class);
            if (o == null || !o.has("best") || !o.get("best").isJsonArray()) return out;
            for (JsonElement e : o.getAsJsonArray("best")) {
                try { out.add(UUID.fromString(e.getAsString())); } catch (RuntimeException ignored) {}
            }
        } catch (RuntimeException ignored) {}
        return out;
    }

    static String serialize(Set<UUID> set) {
        JsonArray arr = new JsonArray();
        for (UUID id : set) arr.add(id.toString());
        JsonObject o = new JsonObject();
        o.add("best", arr);
        return GSON.toJson(o);
    }

    public static synchronized boolean is(UUID id) {
        return id != null && ids().contains(id);
    }

    /** Counts up on every change: lets a sorted list know it is stale. */
    public static synchronized int version() {
        return version;
    }

    /** Adds the friend (or removes them when they already are one); returns whether they are a best friend now. */
    public static synchronized boolean toggle(UUID id) {
        Set<UUID> set = ids();
        boolean now = !set.remove(id);
        if (now) set.add(id);
        version++;
        File f = file();
        if (f != null) {
            try {
                Files.createDirectories(f.getParentFile().toPath());
                Files.write(f.toPath(), serialize(set).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {}
        }
        return now;
    }
}
