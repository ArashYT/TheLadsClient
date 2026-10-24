package com.thelads.core.v26_2.social;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zips the integrated server world to saves/../lads-backups/&lt;world&gt;-&lt;yyyyMMdd-HHmm&gt;.zip
 * before publishing, keeping the newest 5 backups for that world.
 */
public final class BackupHelper26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Social");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private BackupHelper26() {}

    public static void backupWorld(IntegratedServer server) {
        if (server == null) return;
        try {
            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            if (!Files.isDirectory(worldDir)) return;

            String worldName = worldDir.getFileName().toString();
            Path savesDir = worldDir.getParent();
            Path backupDir = savesDir != null && savesDir.getParent() != null
                ? savesDir.getParent().resolve("lads-backups")
                : worldDir.resolveSibling("lads-backups");

            Files.createDirectories(backupDir);

            // Flush saves before zipping
            server.saveEverything(true, true, true);

            String timestamp = LocalDateTime.now().format(FORMATTER);
            Path zipFile = backupDir.resolve(worldName + "-" + timestamp + ".zip");
            if (Files.exists(zipFile)) {
                zipFile = backupDir.resolve(worldName + "-" + timestamp + "-" + System.currentTimeMillis() % 1000 + ".zip");
            }

            LOGGER.info("Creating world backup before P2P publish: {}", zipFile);
            zipDirectory(worldDir, zipFile);

            // Keep only the newest 5 backups for this world
            pruneBackups(backupDir, worldName, 5);
        } catch (Throwable t) {
            LOGGER.error("Failed to create world backup before publishing", t);
        }
    }

    private static void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String fileName = file.getFileName().toString();
                    if ("session.lock".equals(fileName)) {
                        return FileVisitResult.CONTINUE;
                    }

                    String rel = sourceDir.relativize(file).toString().replace('\\', '/');
                    try {
                        zos.putNextEntry(new ZipEntry(rel));
                        try (FileInputStream fis = new FileInputStream(file.toFile())) {
                            fis.transferTo(zos);
                        }
                        zos.closeEntry();
                    } catch (IOException e) {
                        LOGGER.warn("Skipping locked or unreadable file in backup: {}", rel);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static void pruneBackups(Path backupDir, String worldName, int keep) {
        File[] files = backupDir.toFile().listFiles((dir, name) ->
            name.startsWith(worldName + "-") && name.endsWith(".zip"));
        if (files == null || files.length <= keep) return;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - keep;
        for (int i = 0; i < toDelete; i++) {
            if (files[i].delete()) {
                LOGGER.info("Pruned old backup: {}", files[i].getName());
            }
        }
    }
}
