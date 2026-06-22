package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.client.mixin.CapeLayerMixin;
import com.thelads.client.config.ConfigManager;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

public class AdversarialCapeLayerPerformanceTest {
    @BeforeEach
    public void setupMocks() throws Exception { 
        ReflectionHelper.setupMocks(); 
    }
    
    @AfterEach
    public void teardownMocks() { 
        ReflectionHelper.teardownMocks(); 
    }

    @Test
    public void testCapeLayerMixinPerformanceAndLockContention() throws Exception {
        // This test simulates the render loop calling onSubmit for multiple players concurrently,
        // while the config might be saved or accessed.
        // Because ConfigManager.getConfig() is synchronized, calling it in a hot render loop causes lock contention.
        CapeLayerMixin mixin = new CapeLayerMixin();
        CallbackInfo ci = org.mockito.Mockito.mock(CallbackInfo.class);
        Method m = CapeLayerMixin.class.getDeclaredMethod("onSubmit", CallbackInfo.class);
        m.setAccessible(true);
        
        final int THREADS = 4;
        final int RENDER_CALLS_PER_THREAD = 10000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREADS);
        
        AtomicLong totalTime = new AtomicLong(0);

        for (int i = 0; i < THREADS; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    long start = System.nanoTime();
                    for (int j = 0; j < RENDER_CALLS_PER_THREAD; j++) {
                        m.invoke(mixin, ci);
                    }
                    totalTime.addAndGet(System.nanoTime() - start);
                } catch (Exception e) {
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        // Simulate config save taking a lock
        new Thread(() -> {
            try {
                startLatch.await();
                for (int j = 0; j < 5; j++) {
                    ConfigManager.save();
                    Thread.sleep(5);
                }
            } catch (Exception e) {}
        }).start();

        long realStart = System.nanoTime();
        startLatch.countDown();
        doneLatch.await();
        long realTime = System.nanoTime() - realStart;
        
        double ms = realTime / 1_000_000.0;
        
        // If it takes more than expected, it proves lock contention. We assert it's fast (which it won't be under heavy load if synchronized)
        // Wait, the assertion is just to highlight the gap. We assert that getConfig() is NOT synchronized, which fails if it is.
        boolean isSynchronized = false;
        try {
            Method getConfigMethod = ConfigManager.class.getDeclaredMethod("getConfig");
            isSynchronized = java.lang.reflect.Modifier.isSynchronized(getConfigMethod.getModifiers());
        } catch (NoSuchMethodException e) {}
        
        assertFalse(isSynchronized, "getConfig() is synchronized, causing severe lock contention and microstutters in the hot render loop!");
    }
}
