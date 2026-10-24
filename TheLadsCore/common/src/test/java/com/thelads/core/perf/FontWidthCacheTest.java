package com.thelads.core.perf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class FontWidthCacheTest {

    @BeforeEach
    void setUp() {
        FontWidthCache.enabled = true;
        FontWidthCache.clear();
    }

    @Test
    void hitPathReturnsCachedValue() {
        assertNull(FontWidthCache.get("hello"));
        FontWidthCache.put("hello", 25);
        assertEquals(25, FontWidthCache.get("hello"));
    }

    @Test
    void boundedCapacityEnforcedAtMaxEntries() {
        assertEquals(0, FontWidthCache.size());
        for (int i = 0; i < FontWidthCache.MAX_ENTRIES; i++) {
            FontWidthCache.put("key_" + i, i);
        }
        assertEquals(FontWidthCache.MAX_ENTRIES, FontWidthCache.size());

        // Adding one more triggers bound enforcement (flush)
        FontWidthCache.put("overflow_key", 9999);
        assertTrue(FontWidthCache.size() <= FontWidthCache.MAX_ENTRIES);
        assertEquals(9999, FontWidthCache.get("overflow_key"));
    }

    @Test
    void clearFlushesAllEntriesForInvalidation() {
        FontWidthCache.put("text_a", 10);
        FontWidthCache.put("text_b", 20);
        assertEquals(2, FontWidthCache.size());

        FontWidthCache.clear();
        assertEquals(0, FontWidthCache.size());
        assertNull(FontWidthCache.get("text_a"));
        assertNull(FontWidthCache.get("text_b"));
    }

    @Test
    void disabledCacheBypassesLookups() {
        FontWidthCache.put("test", 42);
        assertEquals(42, FontWidthCache.get("test"));

        FontWidthCache.enabled = false;
        assertNull(FontWidthCache.get("test"), "disabled cache must return null");

        FontWidthCache.put("new_key", 100);
        FontWidthCache.enabled = true;
        assertNull(FontWidthCache.get("new_key"), "writes while disabled must be dropped");
    }

    @Test
    void concurrentAccessThreadSafety() throws Exception {
        int threads = 8;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            tasks.add(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    String key = "thread_" + (threadId % 4) + "_key_" + (i % 50);
                    FontWidthCache.put(key, i);
                    Integer val = FontWidthCache.get(key);
                    if (val != null) {
                        assertTrue(val >= 0);
                    }
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        for (Future<Void> future : futures) {
            future.get(); // Throws if any exception occurred in worker thread
        }
        assertTrue(FontWidthCache.size() <= FontWidthCache.MAX_ENTRIES);
    }
}
