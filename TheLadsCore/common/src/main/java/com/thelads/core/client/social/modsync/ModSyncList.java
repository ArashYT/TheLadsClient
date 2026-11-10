package com.thelads.core.client.social.modsync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A profile's own (player-added) mod jars as {"v":1,"mods":[{id,name,version,file,size,sha1,sha512,source,url}]}. The launcher
 * writes it for the profile it starts ({@code <gameDir>/.lads-mod-cache/modsync.json}); a host sends the same text to a guest.
 */
public final class ModSyncList {
    public static final int MAX_MODS = 512, MAX_JSON = 1024 * 1024;

    private ModSyncList() {}

    /** The list in json; IllegalArgumentException (with the reason) for anything but a complete, valid list. */
    public static List<ModSyncEntry> parse(String json) {
        if (json == null || json.length() > MAX_JSON) throw new IllegalArgumentException("mod list too large");
        JsonArray mods;
        try {
            mods = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("mods");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("mod list is not valid JSON");
        }
        if (mods == null) throw new IllegalArgumentException("mod list has no mods");
        if (mods.size() > MAX_MODS) throw new IllegalArgumentException("mod list has more than " + MAX_MODS + " mods");
        List<ModSyncEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement e : mods) {
            if (!e.isJsonObject()) throw new IllegalArgumentException("mod list entry is not an object");
            JsonObject o = e.getAsJsonObject();
            ModSyncEntry entry;
            try {
                entry = new ModSyncEntry(str(o, "id"), str(o, "name"), str(o, "version"), str(o, "file"),
                        o.has("size") ? o.get("size").getAsLong() : 0, str(o, "sha1"), str(o, "sha512"), str(o, "source"), str(o, "url"));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("mod list entry is malformed");
            }
            String problem = entry.problem();
            if (problem != null) throw new IllegalArgumentException("mod list entry refused: " + problem);
            if (seen.add(entry.sha512())) out.add(entry); // the same jar twice is one mod
        }
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    public static String toJson(List<ModSyncEntry> mods) {
        JsonArray array = new JsonArray();
        for (ModSyncEntry m : mods) {
            JsonObject o = new JsonObject();
            o.addProperty("id", m.id());
            o.addProperty("name", m.name());
            o.addProperty("version", m.version());
            o.addProperty("file", m.file());
            o.addProperty("size", m.size());
            o.addProperty("sha1", m.sha1());
            o.addProperty("sha512", m.sha512());
            o.addProperty("source", m.source());
            o.addProperty("url", m.url());
            array.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("v", 1);
        root.add("mods", array);
        return root.toString();
    }

    /** 32 hex characters naming this set of jars whatever their order (presence's game.modsHash); "" for no mods. */
    public static String hash(List<ModSyncEntry> mods) {
        if (mods == null || mods.isEmpty()) return "";
        TreeSet<String> sorted = new TreeSet<>();
        for (ModSyncEntry m : mods) sorted.add(m.sha512());
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String s : sorted) sha.update((s + "\n").getBytes(StandardCharsets.US_ASCII));
            return ModSyncHash.hex(sha.digest()).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The host's jars the guest has to fetch: not one of the guest's own jars (ownSha512, the same bytes) and not a mod the
     * guest's game already loaded in that very version (loaded: mod id to version, which covers the client's own bundle).
     * Another version of a mod the guest has is fetched too: the host's file is the one its world was made with.
     */
    public static List<ModSyncEntry> missing(List<ModSyncEntry> host, Collection<String> ownSha512, Map<String, String> loaded) {
        List<ModSyncEntry> out = new ArrayList<>();
        for (ModSyncEntry m : host) {
            if (ownSha512.contains(m.sha512())) continue;
            if (!m.id().isEmpty() && !m.version().isEmpty() && m.version().equals(loaded.get(m.id()))) continue;
            out.add(m);
        }
        return out;
    }
}
