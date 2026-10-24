package com.thelads.core;

import com.thelads.core.client.AutoBackupHelper;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileWriter;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

public class AutoBackupTest {
    @Test
    void testZipDirectoryAndPrune() throws Exception {
        File tempDir = File.createTempFile("lads_world_test", "");
        tempDir.delete();
        tempDir.mkdirs();

        File savesDir = new File(tempDir, "saves");
        File worldDir = new File(savesDir, "MyWorld");
        worldDir.mkdirs();

        File levelDat = new File(worldDir, "level.dat");
        try (FileWriter fw = new FileWriter(levelDat)) {
            fw.write("dummy level data");
        }

        File sessionLock = new File(worldDir, "session.lock");
        try (FileWriter fw = new FileWriter(sessionLock)) {
            fw.write("lock");
        }

        File backupDir = AutoBackupHelper.getBackupDir(worldDir);
        assertNotNull(backupDir);
        backupDir.mkdirs();

        File zip1 = new File(backupDir, "MyWorld-2026-01-01_10-00-00.zip");
        AutoBackupHelper.zipDirectory(worldDir, zip1);
        assertTrue(zip1.exists());

        try (ZipFile zf = new ZipFile(zip1)) {
            assertNotNull(zf.getEntry("level.dat"));
            assertNull(zf.getEntry("session.lock"), "session.lock should be excluded");
        }

        // Test pruning: create 3 more zips
        File zip2 = new File(backupDir, "MyWorld-2026-01-02_10-00-00.zip");
        zip2.createNewFile();
        zip2.setLastModified(2000L);

        File zip3 = new File(backupDir, "MyWorld-2026-01-03_10-00-00.zip");
        zip3.createNewFile();
        zip3.setLastModified(3000L);

        // Keep 2 newest backups
        int pruned = AutoBackupHelper.pruneOldBackups(backupDir, 2);
        assertTrue(pruned >= 1);

        for (File f : worldDir.listFiles()) f.delete();
        worldDir.delete();
        savesDir.delete();
        for (File f : backupDir.listFiles()) f.delete();
        backupDir.delete();
        backupDir.getParentFile().delete();
        tempDir.delete();
    }
}
