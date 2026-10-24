package com.thelads.core;

import com.thelads.core.client.util.LogPruner;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class LogPrunerTest {
    @Test
    void testFindLogsToPrune() throws Exception {
        File tempDir = File.createTempFile("lads_logs_test", "");
        tempDir.delete();
        tempDir.mkdirs();

        long now = 100_000_000_000L;
        long oneDay = 24L * 60L * 60L * 1000L;

        List<File> files = new ArrayList<>();

        // Latest log (never deleted)
        File latest = new File(tempDir, "latest.log");
        latest.createNewFile();
        latest.setLastModified(now - 20 * oneDay);
        files.add(latest);

        // Debug log (never deleted)
        File debug = new File(tempDir, "debug.log");
        debug.createNewFile();
        debug.setLastModified(now - 20 * oneDay);
        files.add(debug);

        // Old log (16 days old, default maxAge = 14 days)
        File oldLog = new File(tempDir, "2026-01-01-1.log.gz");
        oldLog.createNewFile();
        oldLog.setLastModified(now - 16 * oneDay);
        files.add(oldLog);

        // Recent log (2 days old)
        File recentLog = new File(tempDir, "2026-01-07-1.log.gz");
        recentLog.createNewFile();
        recentLog.setLastModified(now - 2 * oneDay);
        files.add(recentLog);

        List<File> toPrune = LogPruner.findLogsToPrune(files, 14, 20, now);
        assertEquals(1, toPrune.size());
        assertEquals("2026-01-01-1.log.gz", toPrune.get(0).getName());

        for (File f : tempDir.listFiles()) f.delete();
        tempDir.delete();
    }

    @Test
    void testPruneDirectory() throws Exception {
        File tempDir = File.createTempFile("lads_logs_dir_test", "");
        tempDir.delete();
        tempDir.mkdirs();

        long now = System.currentTimeMillis();
        long oneDay = 24L * 60L * 60L * 1000L;

        File latest = new File(tempDir, "latest.log");
        latest.createNewFile();
        latest.setLastModified(now - 30 * oneDay);

        File debug = new File(tempDir, "debug.log");
        debug.createNewFile();
        debug.setLastModified(now - 30 * oneDay);

        File oldLog = new File(tempDir, "2026-01-01-1.log.gz");
        oldLog.createNewFile();
        oldLog.setLastModified(now - 20 * oneDay);

        File recentLog = new File(tempDir, "2026-01-20-1.log.gz");
        recentLog.createNewFile();
        recentLog.setLastModified(now - 1 * oneDay);

        int pruned = LogPruner.prune(tempDir, 14, 20);
        assertEquals(1, pruned);
        assertTrue(latest.exists(), "latest.log must not be deleted");
        assertTrue(debug.exists(), "debug.log must not be deleted");
        assertFalse(oldLog.exists(), "oldLog.log.gz must be deleted");
        assertTrue(recentLog.exists(), "recentLog.log.gz must remain");

        for (File f : tempDir.listFiles()) f.delete();
        tempDir.delete();
    }
}
