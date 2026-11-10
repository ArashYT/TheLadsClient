package com.thelads.core.client.social.modsync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ModSync in the game folder: the launcher's list of this profile's own jars (host and guest read it), and the guest's side of a
 * sync: ask the host's list, fetch what is missing into the host's own folder, ask the launcher for a restart with those jars.
 * The guest's mods folder and settings are never written.
 */
public final class ModSyncFlow {
    /** The launcher writes the profile's own enabled jars here before every launch. */
    public static final String LIST_FILE = ".lads-mod-cache/modsync.json";
    /** Synced jars: {@code lads-modsync/<host uuid>/<ModSyncEntry.localName()>}; the hosts the player trusted: trusted.json. */
    public static final String FOLDER = "lads-modsync", TRUSTED_FILE = "trusted.json";
    /** The restart request the launcher takes when the game closes. */
    public static final String RELAUNCH_FILE = "lads-modsync.json";
    /** Set by the launcher on a ModSync restart: the host whose jars this game runs. */
    public static final String HOST_PROPERTY = "lads.modsync.host";

    public enum Result { CONNECT, RELAUNCH, CANCELLED }

    /** The guest's screen. */
    public interface Ui {
        void status(String text);

        /** The one question per host for jars that exist only on the host's PC; waits for the answer, false cancels the join. */
        boolean trust(List<ModSyncEntry> jars);

        default boolean cancelled() { return false; }
    }

    private ModSyncFlow() {}

    /** This profile's own jars that are in its mods folder as listed (empty: no list, a pack profile, or nothing installed). */
    public static List<ModSyncServer.Shared> shared(Path gameDir) {
        List<ModSyncServer.Shared> out = new ArrayList<>();
        if (gameDir == null) return out;
        try {
            Path list = gameDir.resolve(LIST_FILE), mods = gameDir.resolve("mods");
            if (!Files.isRegularFile(list) || Files.size(list) > ModSyncList.MAX_JSON) return out;
            for (ModSyncEntry m : ModSyncList.parse(Files.readString(list))) {
                Path jar = mods.resolve(m.file());
                if (mods.equals(jar.getParent()) && Files.isRegularFile(jar) && Files.size(jar) == m.size()) out.add(new ModSyncServer.Shared(m, jar));
            }
        } catch (IOException | IllegalArgumentException e) { // InvalidPathException too: nothing to share
            out.clear();
        }
        return out;
    }

    /**
     * The launcher that started this game wrote its mod list, so it also takes the restart request when the game closes. Without
     * it (an older launcher, a game started another way) the game never syncs: it would close and nothing would start it again.
     */
    public static boolean launcherReady(Path gameDir) {
        return gameDir != null && Files.isRegularFile(gameDir.resolve(LIST_FILE));
    }

    /** presence's game.modsHash for this profile. */
    public static String hash(List<ModSyncServer.Shared> shared) {
        return ModSyncList.hash(shared.stream().map(ModSyncServer.Shared::entry).toList());
    }

    /** This game was started by the launcher with that host's jars. */
    public static boolean relaunchedFor(UUID host) {
        return host != null && host.toString().equalsIgnoreCase(System.getProperty(HOST_PROPERTY, ""));
    }

    /**
     * The guest's sync over stream 2, before the game connects. CONNECT: nothing to fetch, join as usual. RELAUNCH: every missing jar
     * is in the host's folder with the listed size and SHA-512, the restart request is written and the host was told. Jars from
     * Modrinth or CurseForge download without a question; a jar that only the host has (or whose download failed) needs the trust
     * question once per host. IOException: the sync failed, nothing may connect.
     */
    public static Result guest(Path gameDir, UUID host, InputStream in, OutputStream out, Map<String, String> loaded,
                               ModSyncDownloader.Http http, boolean relaunched, Ui ui) throws IOException {
        ModSyncClient client = new ModSyncClient(in, out);
        ui.status("Checking mods");
        Set<String> own = new HashSet<>();
        for (ModSyncServer.Shared s : shared(gameDir)) own.add(s.entry().sha512());
        List<ModSyncEntry> missing = ModSyncList.missing(client.list(), own, loaded);
        if (missing.isEmpty()) return Result.CONNECT;

        Path dir = gameDir.resolve(FOLDER).resolve(host.toString());
        // Fabric Loader splits fabric.addMods at this character: a folder with it in its path cannot be added to a launch
        if (dir.toString().contains(java.io.File.pathSeparator)) throw new IOException("ModSync: the game folder's path cannot hold synced mods");
        Files.createDirectories(dir);
        ModSyncSource.Plan plan = ModSyncSource.plan(missing);
        List<ModSyncEntry> fromHost = new ArrayList<>(plan.p2p());
        int at = 0, fetched = 0;
        for (ModSyncEntry m : plan.site()) {
            if (ui.cancelled()) return Result.CANCELLED;
            Path target = dir.resolve(m.localName());
            at++;
            if (ModSyncHash.matches(target, m)) continue; // from an earlier visit
            ui.status("Downloading mod " + at + " of " + missing.size() + ": " + label(m));
            try {
                ModSyncDownloader.download(m, target, http, n -> {});
                fetched++;
            } catch (IOException e) {
                fromHost.add(m);
                at--;
            }
        }
        fromHost.removeIf(m -> ModSyncHash.matches(dir.resolve(m.localName()), m));
        // Started with these very jars already and the game still lacks some (Fabric took another version): never a restart loop.
        if (relaunched && fetched == 0 && fromHost.isEmpty()) return Result.CONNECT;
        if (!fromHost.isEmpty()) {
            if (!trusted(gameDir).contains(host.toString())) {
                if (!ui.trust(List.copyOf(fromHost))) return Result.CANCELLED;
                trust(gameDir, host);
            }
            at = missing.size() - fromHost.size();
            for (ModSyncEntry m : fromHost) {
                if (ui.cancelled()) return Result.CANCELLED;
                ui.status("Getting mod " + (++at) + " of " + missing.size() + " from the host: " + label(m));
                client.fetch(m, dir.resolve(m.localName()), n -> {});
            }
        }
        if (ui.cancelled()) return Result.CANCELLED;
        ui.status("Restarting the game with " + missing.size() + (missing.size() == 1 ? " mod" : " mods"));
        writeRelaunch(gameDir, host, missing);
        client.byeRelaunch();
        return Result.RELAUNCH;
    }

    private static String label(ModSyncEntry m) {
        return m.name().isEmpty() ? m.localName() : m.name();
    }

    /** {host, mods: [{file, size, sha512}]}: the launcher checks every file again before it adds the folder to the launch. */
    static void writeRelaunch(Path gameDir, UUID host, List<ModSyncEntry> mods) throws IOException {
        JsonArray array = new JsonArray();
        for (ModSyncEntry m : mods) {
            JsonObject o = new JsonObject();
            o.addProperty("file", m.localName());
            o.addProperty("size", m.size());
            o.addProperty("sha512", m.sha512());
            array.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("host", host.toString());
        root.add("mods", array);
        Path tmp = gameDir.resolve(RELAUNCH_FILE + ".tmp");
        Files.writeString(tmp, root.toString());
        Files.move(tmp, gameDir.resolve(RELAUNCH_FILE), StandardCopyOption.REPLACE_EXISTING);
    }

    /** The join was cancelled after the request was written: the launcher must not restart the game. */
    public static void dropRelaunch(Path gameDir) {
        try {
            Files.deleteIfExists(gameDir.resolve(RELAUNCH_FILE));
        } catch (IOException ignored) {
        }
    }

    /** Hosts (uuid) whose own jars the player agreed to run. */
    static Set<String> trusted(Path gameDir) {
        Set<String> out = new HashSet<>();
        try {
            Path file = gameDir.resolve(FOLDER).resolve(TRUSTED_FILE);
            if (!Files.isRegularFile(file) || Files.size(file) > 256 * 1024) return out;
            for (JsonElement e : JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("hosts")) out.add(e.getAsString());
        } catch (IOException | RuntimeException e) { // unreadable: nobody is trusted, the question comes again
            out.clear();
        }
        return out;
    }

    static void trust(Path gameDir, UUID host) throws IOException {
        Set<String> hosts = trusted(gameDir);
        hosts.add(host.toString());
        JsonArray array = new JsonArray();
        for (String h : hosts) array.add(h);
        JsonObject root = new JsonObject();
        root.add("hosts", array);
        Files.createDirectories(gameDir.resolve(FOLDER));
        Files.writeString(gameDir.resolve(FOLDER).resolve(TRUSTED_FILE), root.toString());
    }
}
