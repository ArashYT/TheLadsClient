package com.thelads.core.shared;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-process lock on a lock file shared with The Lads Launcher, which opens the same file with FileShare.None.
 * One ReentrantLock per path serialises threads of this JVM (FileChannel locks are per JVM and would otherwise throw
 * OverlappingFileLockException); only the outermost holder takes the OS lock, and the channel is closed after every use.
 */
public final class FileLocks {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface IOSupplier<T> {
        T get() throws IOException;
    }

    private FileLocks() {
    }

    /** Runs the action while holding the lock. On timeout it logs a warning and still runs the action, so user edits are never dropped. */
    public static <T> T withLock(Path lockFile, long timeoutMillis, IOSupplier<T> action) throws IOException {
        Path key = lockFile.toAbsolutePath().normalize();
        ReentrantLock local = LOCKS.computeIfAbsent(key, k -> new ReentrantLock());
        local.lock();
        try {
            if (local.getHoldCount() > 1) {
                return action.get();
            }
            FileChannel channel = null;
            FileLock lock = null;
            long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
            while (true) {
                try {
                    Path parent = key.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    channel = FileChannel.open(key, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    lock = channel.tryLock();
                    if (lock != null) {
                        break;
                    }
                } catch (FileSystemException | OverlappingFileLockException busy) {
                    // Held by the launcher (FileShare.None) or by another process's lock: retry below.
                }
                if (channel != null) {
                    channel.close();
                    channel = null;
                }
                if (System.nanoTime() >= deadline) {
                    LOG.warn("Timed out after {} ms waiting for {}; continuing without the lock", timeoutMillis, key);
                    break;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Interrupted while waiting for {}; continuing without the lock", key);
                    break;
                }
            }
            try {
                return action.get();
            } finally {
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (IOException releaseFailure) {
                        LOG.warn("Could not release {}", key, releaseFailure);
                    }
                }
                if (channel != null) {
                    channel.close();
                }
            }
        } finally {
            local.unlock();
        }
    }
}
