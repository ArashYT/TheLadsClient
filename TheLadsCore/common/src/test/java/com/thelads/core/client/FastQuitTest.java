package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FastQuitTest {
    private final CountDownLatch release = new CountDownLatch(1);

    /** A stand-in "Server thread" that keeps saving until released. */
    private Thread server(String name) {
        Thread thread = new Thread(() -> {
            try { release.await(); } catch (InterruptedException ignored) {}
        }, name);
        thread.start();
        return thread;
    }

    @AfterEach void cleanUp() {
        release.countDown();
        FastQuit.reset();
        System.clearProperty("lads.fastquit");
    }

    @Test void aWorldIsSavingUntilItsServerThreadEnds() throws Exception {
        Path saves = Path.of("build", "fastquit-test", "saves");
        Thread thread = server("Server thread");
        FastQuit.track(saves.resolve("New World"), "New World", thread);

        assertTrue(FastQuit.saving(saves.resolve("New World")));
        assertTrue(FastQuit.saving(saves.resolve("new world").resolve(".")), "same folder: case and '.' don't matter");
        assertFalse(FastQuit.saving(saves.resolve("Other World")));
        assertTrue(FastQuit.anySaving());
        assertEquals(1, FastQuit.pending().size());
        assertTrue(FastQuit.status().startsWith("Saving New World... "), FastQuit.status());
        assertFalse(FastQuit.awaitAll(50), "still saving after the timeout");

        release.countDown();
        assertTrue(FastQuit.awaitAll(5_000));
        assertFalse(thread.isAlive());
        assertFalse(FastQuit.saving(saves.resolve("New World")));
        assertFalse(FastQuit.anySaving());
        assertEquals("", FastQuit.status());
    }

    @Test void theWaitRendersFramesUntilEverySaveEnded() {
        Path saves = Path.of("build", "fastquit-test", "saves");
        FastQuit.track(saves.resolve("A"), "A", server("Server thread A"));
        FastQuit.track(saves.resolve("B"), "B", server("Server thread B"));
        assertTrue(FastQuit.status().contains("Saving A... ") && FastQuit.status().contains("Saving B... "), FastQuit.status());

        AtomicInteger frames = new AtomicInteger();
        long waited = FastQuit.waitWhileSaving(() -> { if (frames.incrementAndGet() == 3) release.countDown(); });
        assertTrue(frames.get() >= 3, "frames keep coming while the servers save");
        assertTrue(waited >= 20, "at most 100 frames a second: " + waited + " ms for " + frames.get() + " frames");
        assertFalse(FastQuit.anySaving());
        assertEquals(0, FastQuit.waitWhileSaving(() -> fail("no frame when nothing is saving")));
    }

    @Test void theKillSwitchTurnsItOff() {
        assertTrue(FastQuit.enabled(), "on by default (no FastQuit mod in a unit test)");
        FastQuit.reset();
        System.setProperty("lads.fastquit", "false");
        assertFalse(FastQuit.enabled());
        assertFalse(FastQuit.otherFastQuitLoaded(), "no Fabric Loader or Forge here: nothing to stand down for");
    }
}
