package com.thelads.core.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates timestamped zip archives of singleplayer worlds upon world close and prunes old backups.
 */
public final class AutoBackupHelper {
    private AutoBackupHelper() {}

    public static File getBackupDir(File worldDir) {
        if (worldDir == null) return null;
        File savesDir = worldDir.getParentFile();
        if (savesDir == null) return null;
        File dotMinecraft = savesDir.getParentFile();
        if (dotMinecraft == null) return null;
        return new File(dotMinecraft, "lads-backups" + File.separator + worldDir.getName());
    }

    public static void zipDirectory(File sourceDir, File zipFile) throws IOException {
        if (!sourceDir.exists()) return;
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addFolderToZip(sourceDir, sourceDir.getAbsolutePath().length() + 1, zos);
        }
    }

    private static void addFolderToZip(File folder, int prefixLen, ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;
        byte[] buffer = new byte[8192];
        for (File file : files) {
            if (file.getName().equals("session.lock")) continue; // skip lock file
            if (file.isDirectory()) {
                addFolderToZip(file, prefixLen, zos);
            } else {
                String entryName = file.getAbsolutePath().substring(prefixLen).replace('\\', '/');
                ZipEntry ze = new ZipEntry(entryName);
                ze.setTime(file.lastModified());
                zos.putNextEntry(ze);
                try (FileInputStream in = new FileInputStream(file)) {
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    public static int pruneOldBackups(File backupDir, int maxBackups) {
        if (backupDir == null || !backupDir.isDirectory() || maxBackups <= 0) return 0;
        File[] files = backupDir.listFiles((d, name) -> name.endsWith(".zip"));
        if (files == null || files.length <= maxBackups) return 0;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int excess = files.length - maxBackups;
        int pruned = 0;
        for (int i = 0; i < excess; i++) {
            if (files[i].delete()) {
                pruned++;
            }
        }
        return pruned;
    }

    public static CompletableFuture<Void> backupWorldAsync(File worldDir, int maxBackups) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (worldDir == null || !worldDir.isDirectory()) return;
                File backupDir = getBackupDir(worldDir);
                if (backupDir == null) return;
                backupDir.mkdirs();

                String dateStr = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                File zipFile = new File(backupDir, worldDir.getName() + "-" + dateStr + ".zip");
                zipDirectory(worldDir, zipFile);
                pruneOldBackups(backupDir, maxBackups);
            } catch (Throwable ignored) {}
        });
    }
}
