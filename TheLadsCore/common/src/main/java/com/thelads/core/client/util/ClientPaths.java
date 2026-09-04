package com.thelads.core.client.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dynamic path resolution for The Lads Client. Decouples all personal and static paths
 * into standard dynamic directories (%APPDATA%/.theladsclient).
 */
public class ClientPaths {
    private static Path customBaseDir = null;

    public static synchronized void setBaseDir(Path path) {
        customBaseDir = path;
    }

    public static synchronized Path getBaseDir() {
        if (customBaseDir != null) {
            return customBaseDir;
        }
        String env = System.getenv("THELADS_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, ".theladsclient");
            }
        }
        return Path.of(System.getProperty("user.home"), ".theladsclient");
    }

    public static File getAccountsFile() {
        return getBaseDir().resolve("lads_accounts.json").toFile();
    }

    public static File getProfileFile() {
        return getBaseDir().resolve("lads_profile.json").toFile();
    }

    public static File getConfigFile() {
        return getBaseDir().resolve("thelads_config.json").toFile();
    }

    public static File getSkinFile() {
        return getBaseDir().resolve("skin.png").toFile();
    }

    public static Path getSkinCacheDir() {
        Path cache = getBaseDir().resolve("cache").resolve("skins");
        try {
            Files.createDirectories(cache);
        } catch (IOException ignored) {}
        return cache;
    }

    public static Path getSharedDir() {
        Path shared = getBaseDir().resolve("shared");
        try {
            Files.createDirectories(shared);
        } catch (IOException ignored) {}
        return shared;
    }

    public static Path ensureBaseDirExists() {
        Path base = getBaseDir();
        try {
            Files.createDirectories(base);
        } catch (IOException ignored) {}
        return base;
    }
}
