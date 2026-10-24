package com.thelads.core.client.util;

import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.LogPrunerModule;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Periodically prunes old Minecraft log files (.log, .log.gz) to prevent disk storage bloat.
 */
public final class LogPruner {
    private LogPruner() {}

    public static List<File> findLogsToPrune(List<File> allLogs, int maxAgeDays, int maxFiles, long nowMillis) {
        List<File> toDelete = new ArrayList<>();
        if (allLogs == null || allLogs.isEmpty()) return toDelete;

        long maxAgeMillis = (long) maxAgeDays * 24L * 60L * 60L * 1000L;
        List<File> candidates = new ArrayList<>();

        for (File f : allLogs) {
            if (f == null || !f.exists()) continue;
            String name = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.equals("latest.log") || name.equals("debug.log")) continue;
            if (name.endsWith(".log") || name.endsWith(".log.gz")) {
                if (nowMillis - f.lastModified() > maxAgeMillis) {
                    toDelete.add(f);
                } else {
                    candidates.add(f);
                }
            }
        }

        // Check if remaining logs still exceed maxFiles
        if (candidates.size() > maxFiles) {
            candidates.sort(Comparator.comparingLong(File::lastModified));
            int excess = candidates.size() - maxFiles;
            for (int i = 0; i < excess; i++) {
                toDelete.add(candidates.get(i));
            }
        }

        return toDelete;
    }

    public static int prune(File logsDir, int maxAgeDays, int maxFiles) {
        if (logsDir == null || !logsDir.isDirectory()) return 0;
        File[] files = logsDir.listFiles();
        if (files == null) return 0;

        File canonicalDir;
        try {
            canonicalDir = logsDir.getCanonicalFile();
        } catch (IOException e) {
            canonicalDir = logsDir.getAbsoluteFile();
        }

        List<File> list = new ArrayList<>();
        for (File f : files) {
            try {
                if (!f.getCanonicalFile().getParentFile().equals(canonicalDir)) continue;
            } catch (IOException e) {
                continue;
            }
            list.add(f);
        }

        List<File> doomed = findLogsToPrune(list, maxAgeDays, maxFiles, System.currentTimeMillis());
        int count = 0;
        for (File f : doomed) {
            if (f.delete()) {
                count++;
            }
        }
        return count;
    }

    public static int pruneIfEnabled(File logsDir) {
        Module m = ModuleManager.getInstance().getModule(LogPrunerModule.NAME);
        if (m instanceof LogPrunerModule lpm && lpm.isEnabled()) {
            return prune(logsDir, lpm.getMaxAgeDays(), lpm.getMaxFiles());
        }
        return 0;
    }
}
