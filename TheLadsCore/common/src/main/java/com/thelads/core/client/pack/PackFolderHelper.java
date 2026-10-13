package com.thelads.core.client.pack;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure helper for detecting folders vs resource packs and folder hierarchy navigation. */
public final class PackFolderHelper {
    private PackFolderHelper() {}

    /**
     * Checks if a file/directory is a resource pack:
     * - A .zip file
     * - A directory containing pack.mcmeta
     */
    public static boolean isPack(Path path) {
        if (path == null || !Files.exists(path)) return false;
        if (Files.isRegularFile(path)) {
            String name = path.getFileName().toString().toLowerCase();
            return name.endsWith(".zip");
        }
        if (Files.isDirectory(path)) {
            return Files.isRegularFile(path.resolve("pack.mcmeta"));
        }
        return false;
    }

    public static boolean isPack(File file) {
        return file != null && isPack(file.toPath());
    }

    /**
     * Checks if a directory is a subfolder container (not a pack itself):
     * - It is a directory
     * - It does NOT contain pack.mcmeta
     */
    public static boolean isFolder(Path path) {
        if (path == null || !Files.isDirectory(path)) return false;
        return !Files.isRegularFile(path.resolve("pack.mcmeta"));
    }

    public static boolean isFolder(File file) {
        return file != null && isFolder(file.toPath());
    }

    /**
     * Computes the normalized relative path (with forward slashes '/') from root to target.
     * Returns empty string if target is root.
     */
    public static String getRelativePath(Path root, Path target) {
        if (root == null || target == null || root.equals(target)) return "";
        try {
            Path rel = root.relativize(target);
            return rel.toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return target.getFileName().toString();
        }
    }

    /**
     * Returns parent relative path or empty string if at root level.
     * E.g. "PvP/Bedwars" -> "PvP", "PvP" -> "", "" -> null.
     */
    public static String getParentRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;
        int idx = relativePath.lastIndexOf('/');
        if (idx < 0) return "";
        return relativePath.substring(0, idx);
    }

    /**
     * Checks if a pack relative path is considered inside the given folder:
     * - If recursive is false: pack must be immediately inside folder.
     *   e.g. folder="", pack="a.zip" -> true; pack="PvP/a.zip" -> false.
     *   e.g. folder="PvP", pack="PvP/a.zip" -> true; pack="PvP/sub/a.zip" -> false.
     * - If recursive is true: pack can be anywhere inside folder or its subfolders.
     *   e.g. folder="", pack="a.zip" -> true; pack="PvP/a.zip" -> true.
     *   e.g. folder="PvP", pack="PvP/a.zip" -> true; pack="PvP/sub/a.zip" -> true; pack="Other/a.zip" -> false.
     */
    public static boolean isInsideFolder(String packRelPath, String currentFolderRelPath, boolean recursive) {
        if (packRelPath == null) return false;
        String normPack = packRelPath.replace('\\', '/');
        String normFolder = currentFolderRelPath == null ? "" : currentFolderRelPath.replace('\\', '/');
        if (normFolder.startsWith("/")) normFolder = normFolder.substring(1);
        if (normFolder.endsWith("/")) normFolder = normFolder.substring(0, normFolder.length() - 1);
        if (normPack.startsWith("/")) normPack = normPack.substring(1);

        if (normFolder.isEmpty()) {
            if (recursive) return true;
            return !normPack.contains("/");
        }

        String prefix = normFolder + "/";
        if (!normPack.startsWith(prefix)) return false;

        if (recursive) return true;
        String remainder = normPack.substring(prefix.length());
        return !remainder.contains("/");
    }

    /**
     * Finds direct subfolders inside currentDir that are folders (not packs).
     */
    public static List<Path> findSubfolders(Path currentDir) {
        if (currentDir == null || !Files.isDirectory(currentDir)) return Collections.emptyList();
        List<Path> subfolders = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
            for (Path entry : stream) {
                if (isFolder(entry)) {
                    subfolders.add(entry);
                }
            }
        } catch (IOException ignored) {}
        subfolders.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getFileName().toString(), b.getFileName().toString()));
        return subfolders;
    }

    /**
     * Recursively scans for all packs under root.
     */
    public static List<Path> scanAllPacks(Path root) {
        if (root == null || !Files.isDirectory(root)) return Collections.emptyList();
        List<Path> packs = new ArrayList<>();
        scanPacksRecursive(root, packs);
        packs.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getFileName().toString(), b.getFileName().toString()));
        return packs;
    }

    private static void scanPacksRecursive(Path dir, List<Path> result) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (isPack(entry)) {
                    result.add(entry);
                } else if (isFolder(entry)) {
                    scanPacksRecursive(entry, result);
                }
            }
        } catch (IOException ignored) {}
    }
}
