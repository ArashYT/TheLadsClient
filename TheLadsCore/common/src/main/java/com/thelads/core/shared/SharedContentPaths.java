package com.thelads.core.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The shared Minecraft folder that every Lads profile uses for worlds, resource packs, shader packs, the server list and
 * screenshots. The launcher passes it to the game as {@value #ENV}. Without that variable (dev runs, other launchers)
 * the server-list redirect stays off and only screenshots use the operating system's default .minecraft, as before.
 */
public final class SharedContentPaths {
    public static final String ENV = "LADS_GLOBAL_MINECRAFT_DIR";
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static volatile Resolved resolved;
    private static volatile boolean worldsAndPacksIsolated;

    record Resolved(Path root, boolean redirect) {
    }

    private SharedContentPaths() {
    }

    /** True only when {@value #ENV} holds a valid absolute path; otherwise Minecraft keeps its own game-folder paths. */
    public static boolean redirectEnabled() {
        return resolved().redirect();
    }

    /** The shared folder from {@value #ENV}, else the operating system's default .minecraft folder. */
    public static Path root() {
        return resolved().root();
    }

    /**
     * Minecraft 1.8.9 corrupts a world saved by a newer version and cannot read newer packs, so the launcher gives it its own
     * saves, resource packs and shader packs. Its Core calls this first; the shared folders below then throw instead of
     * resolving. The server list and screenshots stay shared.
     */
    public static void isolateWorldsAndPacks() {
        worldsAndPacksIsolated = true;
    }

    public static Path savesDir() {
        return worldsAndPacks("saves");
    }

    public static Path resourcePacksDir() {
        return worldsAndPacks("resourcepacks");
    }

    public static Path shaderPacksDir() {
        return worldsAndPacks("shaderpacks");
    }

    private static Path worldsAndPacks(String folder) {
        if (worldsAndPacksIsolated)
            throw new IllegalStateException("This game version keeps its own " + folder + "; the shared " + folder + " folder must never be used here");
        return root().resolve(folder);
    }

    public static Path screenshotsDir() {
        return root().resolve("screenshots");
    }

    public static Path serversFile() {
        return root().resolve("servers.dat");
    }

    /** Holds {@code <root>/.lads-servers.lock}, the lock The Lads Launcher also takes around every write of servers.dat. */
    public static <T> T withServersLock(FileLocks.IOSupplier<T> action) throws IOException {
        return FileLocks.withLock(root().resolve(".lads-servers.lock"), 3_000, action);
    }

    /** QA guard: true only when the redirect is active and the shared folder's real path lies inside {@code directory}. */
    public static boolean redirectedInside(Path directory) throws IOException {
        return redirectEnabled() && Files.isDirectory(root()) && Files.isDirectory(directory)
            && root().toRealPath().startsWith(directory.toRealPath());
    }

    /** Tests only: pins the shared folder (redirect enabled), or restores environment resolution and shared worlds and packs with {@code null}. */
    public static void setRootForTests(Path root) {
        resolved = root == null ? null : new Resolved(root.toAbsolutePath().normalize(), true);
        if (root == null) worldsAndPacksIsolated = false;
    }

    private static Resolved resolved() {
        Resolved current = resolved;
        if (current == null) {
            synchronized (SharedContentPaths.class) {
                current = resolved;
                if (current == null) {
                    current = resolve(System.getenv(), System.getProperty("os.name", ""), System.getProperty("user.home"));
                    resolved = current;
                }
            }
        }
        return current;
    }

    static Resolved resolve(Map<String, String> env, String osName, String userHome) {
        Path fallback = defaultRoot(env.get("APPDATA"), osName, userHome);
        String value = env.get(ENV);
        if (value == null || value.isBlank()) {
            return new Resolved(fallback, false);
        }
        try {
            Path root = Path.of(value.trim());
            if (!root.isAbsolute()) {
                throw new InvalidPathException(value, "the path must be absolute");
            }
            return new Resolved(root.normalize(), true);
        } catch (InvalidPathException invalid) {
            LOG.error("Ignoring {}='{}' ({}). Worlds, packs and the server list use this game folder for this session; "
                + "launch from The Lads Launcher to share them.", ENV, value, invalid.getMessage());
            return new Resolved(fallback, false);
        }
    }

    private static Path defaultRoot(String appData, String osName, String userHome) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return Path.of(appData == null ? userHome : appData, ".minecraft");
        }
        return os.contains("mac") ? Path.of(userHome, "Library", "Application Support", "minecraft") : Path.of(userHome, ".minecraft");
    }
}
