package com.thelads.core.perf;

import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static org.junit.jupiter.api.Assertions.*;

public class FastRenderMathTest {

    private static final float EPSILON = 1e-6f;
    private static final float STRICT_EPSILON = 1.8e-7f;

    @Test
    public void testCardinalAngles() {
        assertEquals(0.0f, FastRenderMath.sin(0.0f), STRICT_EPSILON);
        assertEquals(1.0f, FastRenderMath.cos(0.0f), STRICT_EPSILON);

        float halfPi = (float) (Math.PI / 2.0);
        assertEquals(1.0f, FastRenderMath.sin(halfPi), STRICT_EPSILON);
        assertEquals(0.0f, FastRenderMath.cos(halfPi), STRICT_EPSILON);

        float pi = (float) Math.PI;
        assertEquals(0.0f, FastRenderMath.sin(pi), STRICT_EPSILON);
        assertEquals(-1.0f, FastRenderMath.cos(pi), STRICT_EPSILON);

        float threeHalfPi = (float) (3.0 * Math.PI / 2.0);
        assertEquals(-1.0f, FastRenderMath.sin(threeHalfPi), STRICT_EPSILON);
        assertEquals(0.0f, FastRenderMath.cos(threeHalfPi), STRICT_EPSILON);

        float twoPi = (float) (2.0 * Math.PI);
        assertEquals(0.0f, FastRenderMath.sin(twoPi), STRICT_EPSILON);
        assertEquals(1.0f, FastRenderMath.cos(twoPi), STRICT_EPSILON);

        assertEquals(-1.0f, FastRenderMath.sin(-halfPi), STRICT_EPSILON);
        assertEquals(0.0f, FastRenderMath.cos(-halfPi), STRICT_EPSILON);
    }

    @Test
    public void testTrigAccuracyAcrossWideRange() {
        double maxSinError = 0.0;
        double maxCosError = 0.0;

        int samples = 1_000_000;
        double range = 100.0 * Math.PI;
        double step = (2.0 * range) / samples;

        for (int i = 0; i <= samples; i++) {
            float rad = (float) (-range + i * step);
            float expectedSin = (float) Math.sin(rad);
            float actualSin = FastRenderMath.sin(rad);
            double errorSin = Math.abs(expectedSin - actualSin);
            if (errorSin > maxSinError) maxSinError = errorSin;

            float expectedCos = (float) Math.cos(rad);
            float actualCos = FastRenderMath.cos(rad);
            double errorCos = Math.abs(expectedCos - actualCos);
            if (errorCos > maxCosError) maxCosError = errorCos;
        }

        assertTrue(maxSinError <= 1.8e-7, "Max sin error exceeded 1.8e-7: " + maxSinError);
        assertTrue(maxCosError <= 1.8e-7, "Max cos error exceeded 1.8e-7: " + maxCosError);
        assertTrue(maxSinError <= EPSILON, "Max sin error exceeded 1e-6: " + maxSinError);
        assertTrue(maxCosError <= EPSILON, "Max cos error exceeded 1e-6: " + maxCosError);
    }

    @Test
    public void testPythagoreanIdentity() {
        int samples = 10_000;
        float step = (float) (4.0 * Math.PI / samples);

        for (int i = 0; i < samples; i++) {
            float rad = (float) (-2.0 * Math.PI + i * step);
            float s = FastRenderMath.sin(rad);
            float c = FastRenderMath.cos(rad);
            float pyth = s * s + c * c;
            assertEquals(1.0f, pyth, 2.0e-6f, "Pythagorean identity failed at rad=" + rad);
        }
    }

    @Test
    public void testSpecialFloatValues() {
        assertTrue(Float.isNaN(FastRenderMath.sin(Float.NaN)));
        assertTrue(Float.isNaN(FastRenderMath.cos(Float.NaN)));
        assertTrue(Float.isNaN(FastRenderMath.sin(Float.POSITIVE_INFINITY)));
        assertTrue(Float.isNaN(FastRenderMath.cos(Float.POSITIVE_INFINITY)));
        assertTrue(Float.isNaN(FastRenderMath.sin(Float.NEGATIVE_INFINITY)));
        assertTrue(Float.isNaN(FastRenderMath.cos(Float.NEGATIVE_INFINITY)));
    }

    @Test
    public void testTransformVec3f() {
        // Identity matrix
        float[] id = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
        float[] out = new float[3];
        FastRenderMath.transformVec3f(id, 2.5f, 3.5f, 4.5f, out);
        assertEquals(2.5f, out[0], 1e-6f);
        assertEquals(3.5f, out[1], 1e-6f);
        assertEquals(4.5f, out[2], 1e-6f);

        // Matrix with scale (2, 3, 4) and translation (10, 20, 30)
        float[] m = {
            2,  0,  0, 0,
            0,  3,  0, 0,
            0,  0,  4, 0,
            10, 20, 30, 1
        };
        FastRenderMath.transformVec3f(m, 1.0f, 2.0f, 3.0f, out);
        // x' = 2*1 + 10 = 12
        // y' = 3*2 + 20 = 26
        // z' = 4*3 + 30 = 42
        assertEquals(12.0f, out[0], 1e-6f);
        assertEquals(26.0f, out[1], 1e-6f);
        assertEquals(42.0f, out[2], 1e-6f);
    }

    @Test
    public void testTransformQuad() {
        float[] m = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            5, 10, 15, 1
        };
        float[] src = {
            0, 0, 0,
            1, 0, 0,
            1, 1, 0,
            0, 1, 0
        };
        float[] dst = new float[12];
        FastRenderMath.transformQuad(m, src, 0, dst, 0, 3);

        assertEquals(5.0f, dst[0], 1e-6f);
        assertEquals(10.0f, dst[1], 1e-6f);
        assertEquals(15.0f, dst[2], 1e-6f);

        assertEquals(6.0f, dst[3], 1e-6f);
        assertEquals(10.0f, dst[4], 1e-6f);
        assertEquals(15.0f, dst[5], 1e-6f);

        assertEquals(6.0f, dst[6], 1e-6f);
        assertEquals(11.0f, dst[7], 1e-6f);
        assertEquals(15.0f, dst[8], 1e-6f);

        assertEquals(5.0f, dst[9], 1e-6f);
        assertEquals(11.0f, dst[10], 1e-6f);
        assertEquals(15.0f, dst[11], 1e-6f);
    }

    @Test
    public void testNormalTransforms() {
        // Orthogonal rotation (90 deg around Z)
        float[] rotZ = {
            0, 1, 0, 0,
            -1, 0, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
        float[] out = new float[3];
        FastRenderMath.normalTransformOrthogonal(rotZ, 1.0f, 0.0f, 0.0f, out);
        assertEquals(0.0f, out[0], 1e-6f);
        assertEquals(1.0f, out[1], 1e-6f);
        assertEquals(0.0f, out[2], 1e-6f);

        // General affine with non-uniform scale S(1, 2, 4)
        // Tangent t = (1, 0, 0), normal n = (0, 1, 0)
        // Scale m:
        float[] nonUniformScale = {
            1, 0, 0, 0,
            0, 2, 0, 0,
            0, 0, 4, 0,
            0, 0, 0, 1
        };
        // Transformed tangent t' = (1*1, 2*0, 4*0) = (1, 0, 0)
        FastRenderMath.normalTransformAffine(nonUniformScale, 0.0f, 1.0f, 0.0f, out);
        // Transformed normal n' should be (0, 1, 0), dot(t', n') = 0
        assertEquals(0.0f, out[0], 1e-6f);
        assertEquals(1.0f, out[1], 1e-6f);
        assertEquals(0.0f, out[2], 1e-6f);

        // Arbitrary tangent (1, 1, 0) and normal (-2, 1, 0) (not orthogonal initially)
        // Let's test tangent along (1, 2, 0), perpendicular normal (-2, 1, 0):
        // dot = 1*(-2) + 2*1 = 0
        // Under nonUniformScale S(1, 4, 2):
        // m0=1, m5=4, m10=2
        // t' = (1*1, 2*4, 0*2) = (1, 8, 0)
        float[] scaleMatrix = {
            1, 0, 0, 0,
            0, 4, 0, 0,
            0, 0, 2, 0,
            0, 0, 0, 1
        };
        FastRenderMath.normalTransform(scaleMatrix, -2.0f, 1.0f, 0.0f, out);
        // Check orthogonality of n' and t': n'[0]*1 + n'[1]*8 + n'[2]*0 == 0
        float dot = out[0] * 1.0f + out[1] * 8.0f + out[2] * 0.0f;
        assertEquals(0.0f, dot, 1e-5f, "Transformed normal must remain orthogonal to transformed surface");
    }

    @Test
    public void testZeroAllocations() {
        // Warm up JIT
        float dummy = 0.0f;
        float[] m = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
        float[] vec = new float[3];
        for (int i = 0; i < 50_000; i++) {
            dummy += FastRenderMath.sin((float) i);
            dummy += FastRenderMath.cos((float) i);
            FastRenderMath.transformVec3f(m, 1.0f, 2.0f, 3.0f, vec);
            FastRenderMath.normalTransformOrthogonal(m, 0.0f, 1.0f, 0.0f, vec);
            FastRenderMath.normalTransformAffine(m, 0.0f, 1.0f, 0.0f, vec);
        }

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (threadBean instanceof com.sun.management.ThreadMXBean sunBean && sunBean.isThreadAllocatedMemorySupported()) {
            sunBean.setThreadAllocatedMemoryEnabled(true);
            long before = sunBean.getCurrentThreadAllocatedBytes();

            for (int i = 0; i < 100_000; i++) {
                dummy += FastRenderMath.sin((float) i);
                dummy += FastRenderMath.cos((float) i);
                FastRenderMath.transformVec3f(m, 1.0f, 2.0f, 3.0f, vec);
                FastRenderMath.normalTransformOrthogonal(m, 0.0f, 1.0f, 0.0f, vec);
                FastRenderMath.normalTransformAffine(m, 0.0f, 1.0f, 0.0f, vec);
            }

            long after = sunBean.getCurrentThreadAllocatedBytes();
            long allocated = after - before;
            assertEquals(0, allocated, "Hot math operations must allocate exactly 0 heap bytes, allocated: " + allocated);
        }
        assertNotEquals(Float.NaN, dummy);
    }
}
