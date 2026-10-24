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
        latest.setLastModified(now - 10 * oneDay);
        files.add(latest);

        // Old log (8 days old, maxAge = 7 days)
        File oldLog = new File(tempDir, "2026-01-01-1.log.gz");
        oldLog.createNewFile();
        oldLog.setLastModified(now - 8 * oneDay);
        files.add(oldLog);

        // Recent log (2 days old)
        File recentLog = new File(tempDir, "2026-01-07-1.log.gz");
        recentLog.createNewFile();
        recentLog.setLastModified(now - 2 * oneDay);
        files.add(recentLog);

        List<File> toPrune = LogPruner.findLogsToPrune(files, 7, 10, now);
        assertEquals(1, toPrune.size());
        assertEquals("2026-01-01-1.log.gz", toPrune.get(0).getName());

        for (File f : tempDir.listFiles()) f.delete();
        tempDir.delete();
    }
}
