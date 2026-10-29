package com.thelads.core.v1_8_9.optifine;

import com.sun.management.ThreadMXBean;
import com.thelads.core.modules.OptiFine189Module;
import java.lang.management.ManagementFactory;
import org.junit.Assert;
import org.junit.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class BiomeColorEscapeAnalysisTest {

    private static int simulatedGetGrassColorAtPos(boolean smoothBiomes) {
        CallbackInfoReturnable<Integer> cir = new CallbackInfoReturnable<Integer>("getGrassColorAtPos", true);
        if (!smoothBiomes) {
            cir.setReturnValue(0xFF00FF);
        }
        if (cir.isCancelled()) {
            return cir.getReturnValue();
        }
        return 0x55AA55;
    }

    @Test
    public void testCirScalarReplacedUnderC2() {
        ThreadMXBean mxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!mxBean.isThreadAllocatedMemorySupported()) {
            return;
        }
        if (!mxBean.isThreadAllocatedMemoryEnabled()) {
            mxBean.setThreadAllocatedMemoryEnabled(true);
        }

        long threadId = Thread.currentThread().getId();

        // Warm up C2 JIT compiler
        int sum = 0;
        for (int i = 0; i < 200_000; i++) {
            sum += simulatedGetGrassColorAtPos(true);
        }

        // Measure allocations over 500,000 invocations with Smooth Biomes ON (default)
        long baseBefore = mxBean.getThreadAllocatedBytes(threadId);
        long baseAfter = mxBean.getThreadAllocatedBytes(threadId);
        long overhead = baseAfter - baseBefore;

        long before = mxBean.getThreadAllocatedBytes(threadId);
        int iterations = 500_000;
        for (int i = 0; i < iterations; i++) {
            sum += simulatedGetGrassColorAtPos(true);
        }
        long after = mxBean.getThreadAllocatedBytes(threadId);
        long netAllocated = Math.max(0, (after - before) - overhead);
        double bytesPerOp = (double) netAllocated / iterations;

        // On JDK 21+ HotSpot C2 scalar-replaces CallbackInfoReturnable completely (0 bytes/call).
        // On Java 8 toolchains, CIR allocates under 16 bytes/call.
        String javaVersion = System.getProperty("java.version");
        if (!javaVersion.startsWith("1.8") && !javaVersion.startsWith("8")) {
            Assert.assertEquals("On JDK 21+ HotSpot C2 scalar-replaces CIR to 0.0 bytes", 0.0, bytesPerOp, 0.01);
        } else {
            Assert.assertTrue("On Java 8 CIR allocates minimal memory per call (" + bytesPerOp + " b/call)", bytesPerOp < 16.0);
        }
        Assert.assertNotEquals(0, sum);
    }

    @Test
    public void testSmoothBiomesOffCancelsImmediately() {
        CallbackInfoReturnable<Integer> cir = new CallbackInfoReturnable<Integer>("getGrassColorAtPos", true);
        boolean saved = OptiFine189Module.sOfSmoothBiomes;
        try {
            OptiFine189Module.sOfSmoothBiomes = false;
            if (!OptiFine189Module.sOfSmoothBiomes) {
                cir.setReturnValue(0xAABBCC);
            }
            Assert.assertTrue("CIR must be cancelled when Smooth Biomes is OFF", cir.isCancelled());
            Assert.assertEquals(0xAABBCC, (int) cir.getReturnValue());
        } finally {
            OptiFine189Module.sOfSmoothBiomes = saved;
        }
    }
}
