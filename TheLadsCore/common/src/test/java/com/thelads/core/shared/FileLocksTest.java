package com.thelads.core.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLocksTest {
    @TempDir
    Path temp;

    @Test
    void threadsOfOneGameTakeTurnsInsteadOfFailing() throws Exception {
        Path lock = temp.resolve(".lads-servers.lock");
        AtomicInteger inside = new AtomicInteger();
        AtomicBoolean overlapped = new AtomicBoolean();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(threads.submit(() -> worker(lock, start, inside, overlapped)),
                threads.submit(() -> worker(lock, start, inside, overlapped)));
            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(20, result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            threads.shutdownNow();
        }
        assertFalse(overlapped.get(), "two threads held the servers lock at the same time");
    }

    private static int worker(Path lock, CountDownLatch start, AtomicInteger inside, AtomicBoolean overlapped) throws Exception {
        start.await();
        int done = 0;
        for (int i = 0; i < 20; i++) {
            done += FileLocks.withLock(lock, 10_000, () -> {
                if (inside.incrementAndGet() != 1) overlapped.set(true);
                LockSupport.parkNanos(200_000);
                inside.decrementAndGet();
                // Re-entry on the same thread (a save inside a save) must not deadlock.
                return FileLocks.withLock(lock, 10_000, () -> 1);
            });
        }
        return done;
    }

    @Test
    void waitsForAnotherHolderInThisJvm() throws Exception {
        Path lockFile = temp.resolve("held.lock");
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock held = channel.lock();
            AtomicBoolean ran = new AtomicBoolean();
            Future<Boolean> waiting = thread.submit(() -> FileLocks.withLock(lockFile, 10_000, () -> ran.getAndSet(true)));
            Thread.sleep(300);
            assertFalse(ran.get(), "ran while another channel held the lock");
            held.release();
            assertFalse(waiting.get(10, TimeUnit.SECONDS));
            assertTrue(ran.get());
        } finally {
            thread.shutdownNow();
        }
    }

    @Test
    void timeoutStillRunsTheActionSoTheUsersEditIsKept() throws Exception {
        Path lockFile = temp.resolve("stuck.lock");
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            long startedAt = System.nanoTime();
            assertEquals("saved", FileLocks.withLock(lockFile, 200, () -> "saved"));
            assertTrue(System.nanoTime() - startedAt >= TimeUnit.MILLISECONDS.toNanos(200));
        }
    }

    @Test
    void waitsForAnotherProcess() throws Exception {
        Path lockFile = temp.resolve("process.lock");
        String java = ProcessHandle.current().info().command().orElse("java");
        Process holder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), LockHolder.class.getName(), lockFile.toString())
            .redirectErrorStream(true).start();
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try (BufferedReader output = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8))) {
            assertEquals("locked", output.readLine());
            AtomicBoolean ran = new AtomicBoolean();
            Future<Boolean> waiting = thread.submit(() -> FileLocks.withLock(lockFile, 10_000, () -> ran.getAndSet(true)));
            Thread.sleep(300);
            assertFalse(ran.get(), "ran while another process held the lock");
            holder.getOutputStream().close();
            assertFalse(waiting.get(10, TimeUnit.SECONDS));
            assertTrue(ran.get());
            assertTrue(holder.waitFor(10, TimeUnit.SECONDS));
        } finally {
            thread.shutdownNow();
            holder.destroyForcibly();
        }
    }

    /** Second process: holds an OS lock on the file until its stdin closes. */
    public static final class LockHolder {
        public static void main(String[] args) throws Exception {
            try (FileChannel channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                System.out.println("locked");
                System.out.flush();
                while (System.in.read() != -1) {
                    // Wait for the test to close stdin.
                }
            }
        }
    }
}
