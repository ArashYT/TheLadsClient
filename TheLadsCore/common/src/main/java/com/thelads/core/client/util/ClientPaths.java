package com.thelads.core.client.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dynamic path resolution for The Lads Client. Decouples all personal and static paths
 * into standard dynamic directories (%APPDATA%/.theladsclient).
 */
public class ClientPaths {
    private static Path customBaseDir = null;
    private static Path testWorkerFallbackDir = null;

    public static synchronized void setBaseDir(Path path) {
        customBaseDir = path;
    }

    private static boolean isGradleTestWorker() {
        return System.getProperty("org.gradle.test.worker") != null;
    }

    private static synchronized Path getTestWorkerFallbackDir() {
        if (testWorkerFallbackDir == null) {
            try {
                testWorkerFallbackDir = Files.createTempDirectory("thelads-test-worker-");
                testWorkerFallbackDir.toFile().deleteOnExit();
            } catch (IOException e) {
                testWorkerFallbackDir = Path.of(System.getProperty("java.io.tmpdir"), "thelads-test-worker");
            }
        }
        return testWorkerFallbackDir;
    }

    private static Path getOsDefaultBaseDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, ".theladsclient");
            }
        }
        return Path.of(System.getProperty("user.home"), ".theladsclient");
    }

    /**
     * Infers the root .theladsclient directory when given an instance directory such as
     * {@code <root>/profiles/<id>} or {@code <root>/instances/<id>/minecraft}.
     */
    private static Path inferGlobalRootFromPath(Path start) {
        if (start == null) {
            return null;
        }
        try {
            Path current = start.toAbsolutePath().normalize();
            for (Path p = current; p != null; p = p.getParent()) {
                Path fileName = p.getFileName();
                if (fileName != null && ".theladsclient".equalsIgnoreCase(fileName.toString())) {
                    return p;
                }
            }
            Path parent = current.getParent();
            if (parent != null && parent.getFileName() != null) {
                String parentName = parent.getFileName().toString();
                if ("profiles".equalsIgnoreCase(parentName) && parent.getParent() != null) {
                    return parent.getParent();
                }
                if ("instances".equalsIgnoreCase(parentName) && parent.getParent() != null) {
                    return parent.getParent();
                }
                if ("minecraft".equalsIgnoreCase( current.getFileName() != null ? current.getFileName().toString() : "" )) {
                    Path grandParent = parent.getParent();
                    if (grandParent != null && grandParent.getFileName() != null
                            && "instances".equalsIgnoreCase(grandParent.getFileName().toString())
                            && grandParent.getParent() != null) {
                        return grandParent.getParent();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static synchronized Path getBaseDir() {
        if (customBaseDir != null) {
            return customBaseDir;
        }
        String env = System.getenv("THELADS_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        if (isGradleTestWorker()) {
            return getTestWorkerFallbackDir();
        }
        return getOsDefaultBaseDir();
    }

    public static synchronized Path getGlobalBaseDir() {
        if (customBaseDir != null) {
            return customBaseDir;
        }
        String env = System.getenv("THELADS_GLOBAL_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        String localEnv = System.getenv("THELADS_DIR");
        if (localEnv != null && !localEnv.isBlank()) {
            Path inferred = inferGlobalRootFromPath(Path.of(localEnv));
            if (inferred != null) {
                return inferred;
            }
        }
        if (isGradleTestWorker()) {
            return getTestWorkerFallbackDir();
        }
        Path inferredFromCwd = inferGlobalRootFromPath(Path.of(""));
        if (inferredFromCwd != null) {
            return inferredFromCwd;
        }
        return getOsDefaultBaseDir();
    }

    public static File getAccountsFile() {
        return getBaseDir().resolve("lads_accounts.json").toFile();
    }

    public static File getProfileFile() {
        return getBaseDir().resolve("lads_profile.json").toFile();
    }

    /**
     * Collects all known locations where a shared Lads JSON file (such as {@code thelads_config.json}
     * or {@code thelads_profiles.json}) may live across the launcher root, shared folder, active instance,
     * profiles, and modpack instances.
     */
    public static List<Path> getCandidatePathsForSharedFile(String fileName) {
        Set<Path> candidates = new LinkedHashSet<>();
        Path globalDir = getGlobalBaseDir().toAbsolutePath().normalize();
        Path sharedDir = globalDir.resolve("shared");
        Path baseDir = getBaseDir().toAbsolutePath().normalize();

        candidates.add(sharedDir.resolve(fileName));
        candidates.add(globalDir.resolve(fileName));
        candidates.add(baseDir.resolve(fileName));

        if (!isGradleTestWorker() && customBaseDir == null) {
            try {
                Path cwdFile = Path.of(fileName).toAbsolutePath().normalize();
                if (Files.exists(cwdFile)) {
                    candidates.add(cwdFile);
                }
            } catch (Exception ignored) {}
        }

        Path profilesDir = globalDir.resolve("profiles");
        if (Files.isDirectory(profilesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(profilesDir)) {
                for (Path profileDir : stream) {
                    if (Files.isDirectory(profileDir)) {
                        candidates.add(profileDir.toAbsolutePath().normalize().resolve(fileName));
                    }
                }
            } catch (IOException ignored) {}
        }

        Path instancesDir = globalDir.resolve("instances");
        if (Files.isDirectory(instancesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(instancesDir)) {
                for (Path instanceDir : stream) {
                    Path mcDir = instanceDir.resolve("minecraft");
                    if (Files.isDirectory(mcDir)) {
                        candidates.add(mcDir.toAbsolutePath().normalize().resolve(fileName));
                    }
                }
            } catch (IOException ignored) {}
        }

        return new ArrayList<>(candidates);
    }

    /**
     * Returns all target files that should be kept in sync when saving a shared Lads config/profile file.
     */
    public static List<File> getMirrorTargetsForSharedFile(String fileName) {
        Set<Path> targets = new LinkedHashSet<>();
        Path globalDir = getGlobalBaseDir().toAbsolutePath().normalize();
        Path sharedDir = globalDir.resolve("shared");
        Path baseDir = getBaseDir().toAbsolutePath().normalize();

        targets.add(sharedDir.resolve(fileName));
        targets.add(globalDir.resolve(fileName));
        targets.add(baseDir.resolve(fileName));

        Path profilesDir = globalDir.resolve("profiles");
        if (Files.isDirectory(profilesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(profilesDir)) {
                for (Path profileDir : stream) {
                    if (Files.isDirectory(profileDir)) {
                        Path normalized = profileDir.toAbsolutePath().normalize();
                        Path targetFile = normalized.resolve(fileName);
                        if (Files.exists(targetFile) || Files.isDirectory(normalized.resolve("mods")) || Files.exists(normalized.resolve("options.txt"))) {
                            targets.add(targetFile);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }

        Path instancesDir = globalDir.resolve("instances");
        if (Files.isDirectory(instancesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(instancesDir)) {
                for (Path instanceDir : stream) {
                    Path mcDir = instanceDir.resolve("minecraft");
                    if (Files.isDirectory(mcDir)) {
                        Path normalized = mcDir.toAbsolutePath().normalize();
                        Path targetFile = normalized.resolve(fileName);
                        if (Files.exists(targetFile) || Files.isDirectory(normalized.resolve("mods"))) {
                            targets.add(targetFile);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }

        List<File> result = new ArrayList<>(targets.size());
        for (Path p : targets) {
            result.add(p.toFile());
        }
        return result;
    }

    private static File resolveAndSeedSharedFile(String fileName) {
        if (customBaseDir != null) {
            return customBaseDir.resolve(fileName).toFile();
        }
        Path globalDir = getGlobalBaseDir();
        Path sharedDir = globalDir.resolve("shared");
        Path sharedFile = sharedDir.resolve(fileName).toAbsolutePath().normalize();

        try {
            Files.createDirectories(sharedDir);
        } catch (IOException ignored) {}

        Path newestPath = null;
        long newestTime = Long.MIN_VALUE;
        boolean newestHas197Preset = false;
        boolean checkPresetFlag = "thelads_config.json".equals(fileName);

        for (Path candidate : getCandidatePathsForSharedFile(fileName)) {
            try {
                if (Files.isRegularFile(candidate) && Files.size(candidate) > 2) {
                    boolean has197Preset = false;
                    if (checkPresetFlag) {
                        try {
                            String text = Files.readString(candidate);
                            has197Preset = text.contains("\"lads197PresetApplied\"") && text.contains("true");
                        } catch (IOException ignored) {}
                    }
                    long mtime = Files.getLastModifiedTime(candidate).toMillis();
                    if (newestPath == null
                            || (has197Preset && !newestHas197Preset)
                            || (has197Preset == newestHas197Preset && mtime > newestTime)) {
                        newestTime = mtime;
                        newestPath = candidate;
                        newestHas197Preset = has197Preset;
                    }
                }
            } catch (IOException ignored) {}
        }

        if (newestPath != null && !newestPath.equals(sharedFile)) {
            try {
                Files.copy(newestPath, sharedFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException ignored) {
                try {
                    Files.copy(newestPath, sharedFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored2) {
                    return newestPath.toFile();
                }
            }
        }

        return sharedFile.toFile();
    }

    public static File getConfigFile() {
        return resolveAndSeedSharedFile("thelads_config.json");
    }

    public static File getProfilesFile() {
        return resolveAndSeedSharedFile("thelads_profiles.json");
    }

    public static Path getHudProfilesDir() {
        if (customBaseDir != null) {
            Path dir = customBaseDir.resolve("hud_profiles");
            try { Files.createDirectories(dir); } catch (IOException ignored) {}
            return dir;
        }
        Path dir = getSharedDir().resolve("hud_profiles");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
        return dir;
    }

    public static Path getCrosshairDrawingPath() {
        if (customBaseDir != null) {
            return customBaseDir.resolve("config").resolve("thelads").resolve("crosshair-drawing.json");
        }
        Path sharedDrawing = getSharedDir().resolve("crosshair-drawing.json").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(sharedDrawing);
        String env = System.getenv("THELADS_DIR");
        if (env != null && !env.isBlank()) {
            candidates.add(Path.of(env).resolve("config").resolve("thelads").resolve("crosshair-drawing.json").toAbsolutePath().normalize());
        }
        if (!isGradleTestWorker()) {
            candidates.add(Path.of("config", "thelads", "crosshair-drawing.json").toAbsolutePath().normalize());
        }
        candidates.add(getBaseDir().resolve("config").resolve("thelads").resolve("crosshair-drawing.json").toAbsolutePath().normalize());

        Path newest = null;
        long newestTime = Long.MIN_VALUE;
        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate) && Files.size(candidate) > 0) {
                    long mtime = Files.getLastModifiedTime(candidate).toMillis();
                    if (mtime > newestTime) {
                        newestTime = mtime;
                        newest = candidate;
                    }
                }
            } catch (IOException ignored) {}
        }

        if (newest != null && !newest.equals(sharedDrawing)) {
            try {
                Files.createDirectories(sharedDrawing.getParent());
                Files.copy(newest, sharedDrawing, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException ignored) {}
        }
        return sharedDrawing;
    }

    public static File getCrosshairDrawingFile() {
        return getCrosshairDrawingPath().toFile();
    }

    public static File getCrosshairProfilesFile() {
        return resolveAndSeedSharedFile("thelads_crosshair_profiles.json");
    }

    public static Path getCrosshairProfilesPath() {
        return getCrosshairProfilesFile().toPath();
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
