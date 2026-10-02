package com.thelads.core;

import com.thelads.core.modules.ThreadPriorityModule;
import com.thelads.core.config.SliderOption;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThreadPriorityTest {
    @Test void restoresPriorityAndLeavesUnrelatedThreadsAlone() throws Exception {
        var stop = new CountDownLatch(1);
        Runnable wait = () -> { try { stop.await(); } catch (InterruptedException ignored) {} };
        var worker = new Thread(wait, "Worker-LadsPriorityTest"); worker.setPriority(4);
        var unrelated = new Thread(wait, "Unrelated-LadsPriorityTest"); unrelated.setPriority(3);
        var batcher = new Thread(wait, "Chunk Batcher 0"); batcher.setPriority(4); // 1.8.9's chunk worker
        var module = new ThreadPriorityModule();
        worker.start(); unrelated.start(); batcher.start();
        try {
            assertFalse(module.isEnabled());
            ((SliderOption)module.getOption("Worker thread priority")).setValue(6);
            module.setEnabled(true);
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (worker.getPriority() != 6 && System.nanoTime() < deadline) Thread.sleep(10);
            while (batcher.getPriority() != 6 && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(6, worker.getPriority());
            assertEquals(6, batcher.getPriority());
            assertEquals(3, unrelated.getPriority());
            module.setEnabled(false);
            assertEquals(4, worker.getPriority());
            assertEquals(4, batcher.getPriority());
        } finally { module.setEnabled(false); stop.countDown(); worker.join(); unrelated.join(); batcher.join(); }
    }
}
