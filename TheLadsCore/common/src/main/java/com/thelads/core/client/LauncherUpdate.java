package com.thelads.core.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.mods.ModStateStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.LongPredicate;

/**
 * The title screen's Update button, a hand-off like {@link VersionSwitch}: while the launcher holds a downloaded and verified
 * update it keeps {@link #NOTICE_FILE} in the game folder ({"version":"1.13.9","launcher":1234}, its own process id) and deletes
 * it otherwise; the game writes {@link #REQUEST_FILE} and quits; the launcher then installs the update and starts this version
 * again. The game asks no server: only the launcher talks to the update feed.
 */
public final class LauncherUpdate {
    public static final String NOTICE_FILE = "lads_update.json";
    public static final String REQUEST_FILE = "lads_update_request.json";
    private static final long RECHECK_NANOS = 2_000_000_000L;
    private static long checkedAt;
    private static boolean checked;
    private static String cached;

    private LauncherUpdate() {}

    /** The version the Update button installs, or null for no button. The title screens ask every frame: the notice is read at most every 2 s. */
    public static synchronized String available() {
        long now = System.nanoTime();
        if (!checked || now - checkedAt >= RECHECK_NANOS) {
            cached = available(ClientPaths.getBaseDir(), LauncherUpdate::alive);
            checkedAt = now;
            checked = true;
        }
        return cached;
    }

    /** By reflection: the 1.8.9 jar's downgrader swaps a direct ProcessHandle call for its own JNA stub, which fails on that version's
     *  old JNA (no button at all, seen in game), while the game itself runs on a Java that has the real one. */
    static boolean alive(long pid) {
        try {
            Class<?> handle = Class.forName("java.lang.ProcessHandle");
            java.util.Optional<?> found = (java.util.Optional<?>) handle.getMethod("of", long.class).invoke(null, pid);
            return found.isPresent() && (Boolean) handle.getMethod("isAlive").invoke(found.get());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** {@code alive}: whether that process id still runs. A notice whose launcher is gone counts as none: nobody would install it. */
    public static String available(Path dir, LongPredicate alive) {
        try {
            Path file = dir.resolve(NOTICE_FILE);
            if (!Files.isRegularFile(file)) return null;
            JsonElement e = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            if (!e.isJsonObject()) return null;
            JsonObject root = e.getAsJsonObject();
            String version = root.get("version").getAsString().trim();
            long launcher = root.get("launcher").getAsLong();
            return version.matches("\\d+(\\.\\d+){1,3}") && launcher > 0 && alive.test(launcher) ? version : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The button's press: writes the request, then {@code quit} closes the game. Nothing happens when it could not be written. */
    public static void press(Runnable quit) {
        if (request(ClientPaths.getBaseDir())) quit.run();
    }

    /** Writes the request atomically. Returns false when nothing was written. */
    public static boolean request(Path dir) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("requested", Instant.now().toString());
            Files.createDirectories(dir);
            ModStateStore.writeAtomically(dir.resolve(REQUEST_FILE), obj.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
