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

    public static synchronized Path getGlobalBaseDir() {
        if (customBaseDir != null) {
            return customBaseDir;
        }
        String env = System.getenv("THELADS_GLOBAL_DIR");
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
        if (customBaseDir != null) {
            return customBaseDir.resolve("thelads_config.json").toFile();
        }
        Path globalDir = getGlobalBaseDir();
        Path sharedDir = globalDir.resolve("shared");
        Path sharedConfigFile = sharedDir.resolve("thelads_config.json");
        Path rootConfigFile = globalDir.resolve("thelads_config.json");

        // 1. Primary: shared configuration file across all instances & modpacks
        if (Files.exists(sharedConfigFile)) {
            return sharedConfigFile.toFile();
        }

        // 2. If root config exists but shared does not, seed shared from root
        if (Files.exists(rootConfigFile)) {
            try {
                Files.createDirectories(sharedDir);
                Files.copy(rootConfigFile, sharedConfigFile);
                return sharedConfigFile.toFile();
            } catch (IOException ignored) {
                return rootConfigFile.toFile();
            }
        }

        // 3. If local instance config exists (e.g. from THELADS_DIR), seed shared from it
        Path localConfigFile = getBaseDir().resolve("thelads_config.json");
        if (Files.exists(localConfigFile) && !localConfigFile.toAbsolutePath().equals(sharedConfigFile.toAbsolutePath())) {
            try {
                Files.createDirectories(sharedDir);
                Files.copy(localConfigFile, sharedConfigFile);
                return sharedConfigFile.toFile();
            } catch (IOException ignored) {}
        }

        // 4. Default: ensure shared dir exists and return shared config file
        try {
            Files.createDirectories(sharedDir);
        } catch (IOException ignored) {}
        return sharedConfigFile.toFile();
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
        Path shared = getGlobalBaseDir().resolve("shared");
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
