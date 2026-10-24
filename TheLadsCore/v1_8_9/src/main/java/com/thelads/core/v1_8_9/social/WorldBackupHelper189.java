package com.thelads.core.v1_8_9.social;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * World backup on open for 1.8.9: zips the singleplayer world folder to
 * saves/../lads-backups/<world>-<yyyyMMdd-HHmm>.zip before publishing, keeping the newest 5.
 */
public final class WorldBackupHelper189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-Backup");

    private WorldBackupHelper189() {}

    public static void backup(String worldFolder) {
        if (worldFolder == null || worldFolder.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            File savesDir = new File(mc.mcDataDir, "saves");
            File worldDir = new File(savesDir, worldFolder);
            if (!worldDir.isDirectory()) return;

            File backupsDir = new File(mc.mcDataDir, "lads-backups");
            if (!backupsDir.exists()) backupsDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date());
            File zipFile = new File(backupsDir, worldFolder + "-" + timestamp + ".zip");

            LOGGER.info("Creating world backup for {} at {}", worldFolder, zipFile.getAbsolutePath());
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                zipDirectory(worldDir, worldDir, zos);
            }
            LOGGER.info("World backup created successfully: {} bytes", zipFile.length());

            pruneBackups(backupsDir, worldFolder, 5);
        } catch (Throwable t) {
            LOGGER.error("Failed to backup world: {}", worldFolder, t);
        }
    }

    private static void zipDirectory(File root, File dir, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[8192];
        for (File file : files) {
            if (file.isDirectory()) {
                zipDirectory(root, file, zos);
            } else {
                if (file.getName().endsWith(".lock") || "lads-host.json".equals(file.getName())) continue; // skip session.lock and host config
                String relativePath = root.toURI().relativize(file.toURI()).getPath();
                zos.putNextEntry(new ZipEntry(relativePath));
                try (FileInputStream fis = new FileInputStream(file)) {
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private static void pruneBackups(File backupsDir, String worldFolder, int maxToKeep) {
        String prefix = worldFolder + "-";
        File[] backups = backupsDir.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".zip"));
        if (backups != null && backups.length > maxToKeep) {
            Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
            int toDelete = backups.length - maxToKeep;
            for (int i = 0; i < toDelete; i++) {
                LOGGER.info("Pruning old backup: {}", backups[i].getName());
                backups[i].delete();
            }
        }
    }
}
