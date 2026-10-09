package com.thelads.core.perf;

import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical stress test suite for FastRenderMath.
 * Adversarially probes numerical boundaries, extreme float inputs,
 * subnormals/denormals, degenerate affine transformations,
 * and zero-allocation invariants.
 */
public class FastRenderMathStressTest {

    private static final float EPSILON = 1.0e-6f;
    private static final float ORTHOGONALITY_TOLERANCE = 1.0e-5f;

    // =========================================================================
    // 1. Extreme Floats: NaN and Infinities
    // =========================================================================

    @Test
    public void testNaNAndInfinities() {
        // sin(x) must return NaN for non-finite inputs
        assertTrue(Float.isNaN(FastRenderMath.sin(Float.NaN)), "sin(NaN) must be NaN");
        assertTrue(Float.isNaN(FastRenderMath.sin(Float.POSITIVE_INFINITY)), "sin(+Inf) must be NaN");
        assertTrue(Float.isNaN(FastRenderMath.sin(Float.NEGATIVE_INFINITY)), "sin(-Inf) must be NaN");

        // cos(x) must return NaN for non-finite inputs
        assertTrue(Float.isNaN(FastRenderMath.cos(Float.NaN)), "cos(NaN) must be NaN");
        assertTrue(Float.isNaN(FastRenderMath.cos(Float.POSITIVE_INFINITY)), "cos(+Inf) must be NaN");
        assertTrue(Float.isNaN(FastRenderMath.cos(Float.NEGATIVE_INFINITY)), "cos(-Inf) must be NaN");
    }

    // =========================================================================
    // 2. Subnormals and Denormals
    // =========================================================================

    @Test
    public void testSubnormalsAndDenormals() {
        float[] subnormals = {
            Float.MIN_VALUE,               // Smallest positive non-zero float (~1.4e-45)
            -Float.MIN_VALUE,
            Float.MIN_VALUE * 2.0f,
            -Float.MIN_VALUE * 2.0f,
            Float.MIN_VALUE * 100.0f,
            -Float.MIN_VALUE * 100.0f,
            Float.MIN_NORMAL / 2.0f,       // Middle of subnormal range
            -Float.MIN_NORMAL / 2.0f,
            Math.nextDown(Float.MIN_NORMAL), // Largest subnormal float
            -Math.nextDown(Float.MIN_NORMAL),
            Float.MIN_NORMAL,              // Smallest positive normal float (~1.175e-38)
            -Float.MIN_NORMAL,
            0.0f,
            -0.0f
        };

        for (float val : subnormals) {
            float expSin = (float) Math.sin((double) val);
            float actSin = FastRenderMath.sin(val);
            float errSin = Math.abs(expSin - actSin);
            assertTrue(!Float.isNaN(actSin), "sin(" + val + ") returned NaN");
            assertTrue(errSin <= EPSILON, "Subnormal sin error exceeded 1.0e-6 for " + val + ": " + errSin);

            float expCos = (float) Math.cos((double) val);
            float actCos = FastRenderMath.cos(val);
            float errCos = Math.abs(expCos - actCos);
            assertTrue(!Float.isNaN(actCos), "cos(" + val + ") returned NaN");
            assertTrue(errCos <= EPSILON, "Subnormal cos error exceeded 1.0e-6 for " + val + ": " + errCos);
        }

        // Systematic scan over binary exponent levels in subnormal range
        int subnormalBits = 0x00000001;
        while (subnormalBits < 0x00800000) {
            float pos = Float.intBitsToFloat(subnormalBits);
            float neg = -pos;

            float actSinPos = FastRenderMath.sin(pos);
            float expSinPos = (float) Math.sin((double) pos);
            assertTrue(Math.abs(expSinPos - actSinPos) <= EPSILON);

            float actSinNeg = FastRenderMath.sin(neg);
            float expSinNeg = (float) Math.sin((double) neg);
            assertTrue(Math.abs(expSinNeg - actSinNeg) <= EPSILON);

            float actCosPos = FastRenderMath.cos(pos);
            float expCosPos = (float) Math.cos((double) pos);
            assertTrue(Math.abs(expCosPos - actCosPos) <= EPSILON);

            subnormalBits <<= 1;
        }
    }

    // =========================================================================
    // 3. Large Angles [-1000pi, 1000pi]
    // =========================================================================

    @Test
    public void testLargeAnglesThousandPi() {
        double maxSinError = 0.0;
        double maxCosError = 0.0;
        float worstSinRad = 0.0f;
        float worstCosRad = 0.0f;

        double range = 1000.0 * Math.PI; // [-3141.59265, 3141.59265]
        int samples = 2_000_000;
        double step = (2.0 * range) / samples;

        for (int i = 0; i <= samples; i++) {
            float rad = (float) (-range + i * step);

            float expectedSin = (float) Math.sin((double) rad);
            float actualSin = FastRenderMath.sin(rad);
            double errorSin = Math.abs(expectedSin - actualSin);
            if (errorSin > maxSinError) {
                maxSinError = errorSin;
                worstSinRad = rad;
            }

            float expectedCos = (float) Math.cos((double) rad);
            float actualCos = FastRenderMath.cos(rad);
            double errorCos = Math.abs(expectedCos - actualCos);
            if (errorCos > maxCosError) {
                maxCosError = errorCos;
                worstCosRad = rad;
            }
        }

        System.out.printf("[FastRenderMathStressTest] [-1000pi, 1000pi] Max sin error: %.9e at rad=%f%n", maxSinError, worstSinRad);
        System.out.printf("[FastRenderMathStressTest] [-1000pi, 1000pi] Max cos error: %.9e at rad=%f%n", maxCosError, worstCosRad);

        assertTrue(maxSinError <= EPSILON,
            String.format("Max sin error exceeded 1.0e-6 across [-1000pi, 1000pi]: %.9e at rad=%f", maxSinError, worstSinRad));
        assertTrue(maxCosError <= EPSILON,
            String.format("Max cos error exceeded 1.0e-6 across [-1000pi, 1000pi]: %.9e at rad=%f", maxCosError, worstCosRad));
    }

    @Test
    public void testExplicitBoundaryAngles() {
        // Specific boundary angles at integer and half-integer multiples of pi up to 1000pi
        int[] multiples = {
            -1000, -999, -500, -250, -100, -50, -10, -2, -1, 0,
            1, 2, 10, 50, 100, 250, 500, 999, 1000
        };

        for (int m : multiples) {
            float radPi = (float) (m * Math.PI);
            float expSin = (float) Math.sin((double) radPi);
            float actSin = FastRenderMath.sin(radPi);
            assertEquals(expSin, actSin, EPSILON, "Failed at " + m + "*pi");

            float expCos = (float) Math.cos((double) radPi);
            float actCos = FastRenderMath.cos(radPi);
            assertEquals(expCos, actCos, EPSILON, "Failed at " + m + "*pi");

            float radHalfPi = (float) ((m + 0.5) * Math.PI);
            float expSinHalf = (float) Math.sin((double) radHalfPi);
            float actSinHalf = FastRenderMath.sin(radHalfPi);
            assertEquals(expSinHalf, actSinHalf, EPSILON, "Failed at (" + m + "+0.5)*pi");

            float expCosHalf = (float) Math.cos((double) radHalfPi);
            float actCosHalf = FastRenderMath.cos(radHalfPi);
            assertEquals(expCosHalf, actCosHalf, EPSILON, "Failed at (" + m + "+0.5)*pi");
        }
    }

    // =========================================================================
    // 4. Degenerate Matrices: Zero Scale, Negative Scale, Sheared Affine
    // =========================================================================

    @Test
    public void testUniformZeroScaleMatrix() {
        // Completely zero matrix
        float[] allZero = new float[16];
        float[] out = new float[3];

        // Point transform should map to (0, 0, 0)
        FastRenderMath.transformVec3f(allZero, 5.0f, -3.0f, 10.0f, out);
        assertEquals(0.0f, out[0], EPSILON);
        assertEquals(0.0f, out[1], EPSILON);
        assertEquals(0.0f, out[2], EPSILON);

        // Normal transforms on zero matrix: sq = 0 <= 1e-18, must safely output (0, 0, 0) without NaN or crash
        FastRenderMath.normalTransformOrthogonal(allZero, 1.0f, 0.0f, 0.0f, out);
        assertFalse(Float.isNaN(out[0]));
        assertFalse(Float.isNaN(out[1]));
        assertFalse(Float.isNaN(out[2]));
        assertEquals(0.0f, out[0], EPSILON);
        assertEquals(0.0f, out[1], EPSILON);
        assertEquals(0.0f, out[2], EPSILON);

        FastRenderMath.normalTransformAffine(allZero, 1.0f, 0.0f, 0.0f, out);
        assertFalse(Float.isNaN(out[0]));
        assertFalse(Float.isNaN(out[1]));
        assertFalse(Float.isNaN(out[2]));
        assertEquals(0.0f, out[0], EPSILON);
        assertEquals(0.0f, out[1], EPSILON);
        assertEquals(0.0f, out[2], EPSILON);
    }

    @Test
    public void testNonUniformZeroScaleRankDeficientMatrices() {
        // Rank 2: Flattened to XY (Z scale = 0)
        float[] flattenZ = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 1
        };
        float[] out = new float[3];

        FastRenderMath.transformVec3f(flattenZ, 10.0f, 20.0f, 30.0f, out);
        assertEquals(10.0f, out[0], EPSILON);
        assertEquals(20.0f, out[1], EPSILON);
        assertEquals(0.0f,  out[2], EPSILON);

        // Cofactor for Z-flattened matrix:
        // c22 = m0*m5 - m4*m1 = 1*1 - 0 = 1.
        // For normal nz = 1: tz = 1, sq = 1, out = (0, 0, 1)
        FastRenderMath.normalTransformAffine(flattenZ, 0.0f, 0.0f, 1.0f, out);
        assertEquals(0.0f, out[0], EPSILON);
        assertEquals(0.0f, out[1], EPSILON);
        assertEquals(1.0f, out[2], EPSILON);

        // For in-plane normal (1, 0, 0): cofactor transforms to 0, out safely (0, 0, 0)
        FastRenderMath.normalTransformAffine(flattenZ, 1.0f, 0.0f, 0.0f, out);
        assertFalse(Float.isNaN(out[0]));
        assertFalse(Float.isNaN(out[1]));
        assertFalse(Float.isNaN(out[2]));
    }

    @Test
    public void testNegativeScaleMatrices() {
        float[] out = new float[3];

        // 1. Single axis reflection: S(-1, 1, 1)
        float[] reflectX = {
            -1, 0, 0, 0,
             0, 1, 0, 0,
             0, 0, 1, 0,
             0, 0, 0, 1
        };
        FastRenderMath.transformVec3f(reflectX, 3.0f, 4.0f, 5.0f, out);
        assertEquals(-3.0f, out[0], EPSILON);
        assertEquals( 4.0f, out[1], EPSILON);
        assertEquals( 5.0f, out[2], EPSILON);

        // 2. Full 3D inversion: S(-1, -1, -1)
        float[] invert3D = {
            -1,  0,  0, 0,
             0, -1,  0, 0,
             0,  0, -1, 0,
             0,  0,  0, 1
        };
        FastRenderMath.transformVec3f(invert3D, 2.0f, -3.0f, 4.0f, out);
        assertEquals(-2.0f, out[0], EPSILON);
        assertEquals( 3.0f, out[1], EPSILON);
        assertEquals(-4.0f, out[2], EPSILON);

        // 3. Non-uniform negative scaling with translation: S(-2, 3, -4) with T(10, 20, 30)
        float[] negScaleTrans = {
            -2,  0,  0, 0,
             0,  3,  0, 0,
             0,  0, -4, 0,
            10, 20, 30, 1
        };
        FastRenderMath.transformVec3f(negScaleTrans, 1.0f, 2.0f, 3.0f, out);
        // x' = -2*1 + 10 = 8
        // y' =  3*2 + 20 = 26
        // z' = -4*3 + 30 = 18
        assertEquals( 8.0f, out[0], EPSILON);
        assertEquals(26.0f, out[1], EPSILON);
        assertEquals(18.0f, out[2], EPSILON);
    }

    @Test
    public void testShearedAffineMatricesAndOrthogonality() {
        Random rand = new Random(0xCAFEBABE);

        // Test 1000 randomized affine matrices with shear, negative scale, and rotation
        for (int trial = 0; trial < 1000; trial++) {
            // Generate non-singular matrix with shears and non-uniform scales
            float sx = (rand.nextFloat() - 0.5f) * 10.0f;
            float sy = (rand.nextFloat() - 0.5f) * 10.0f;
            float sz = (rand.nextFloat() - 0.5f) * 10.0f;
            if (Math.abs(sx) < 0.1f) sx = 1.0f;
            if (Math.abs(sy) < 0.1f) sy = 1.0f;
            if (Math.abs(sz) < 0.1f) sz = 1.0f;

            float shXY = (rand.nextFloat() - 0.5f) * 2.0f;
            float shXZ = (rand.nextFloat() - 0.5f) * 2.0f;
            float shYZ = (rand.nextFloat() - 0.5f) * 2.0f;

            float tx = (rand.nextFloat() - 0.5f) * 100.0f;
            float ty = (rand.nextFloat() - 0.5f) * 100.0f;
            float tz = (rand.nextFloat() - 0.5f) * 100.0f;

            // Column-major affine matrix:
            // Col 0: [sx, 0, 0, 0]
            // Col 1: [shXY, sy, 0, 0]
            // Col 2: [shXZ, shYZ, sz, 0]
            // Col 3: [tx, ty, tz, 1]
            float[] m = {
                sx,   0.0f, 0.0f, 0.0f,
                shXY, sy,   0.0f, 0.0f,
                shXZ, shYZ, sz,   0.0f,
                tx,   ty,   tz,   1.0f
            };

            // Pick two arbitrary non-collinear tangent vectors
            float t1x = (rand.nextFloat() - 0.5f) * 2.0f;
            float t1y = (rand.nextFloat() - 0.5f) * 2.0f;
            float t1z = (rand.nextFloat() - 0.5f) * 2.0f;

            float t2x = (rand.nextFloat() - 0.5f) * 2.0f;
            float t2y = (rand.nextFloat() - 0.5f) * 2.0f;
            float t2z = (rand.nextFloat() - 0.5f) * 2.0f;

            // Compute surface normal via cross product: n = t1 x t2
            float nx = t1y * t2z - t1z * t2y;
            float ny = t1z * t2x - t1x * t2z;
            float nz = t1x * t2y - t1y * t2x;
            float nLenSq = nx * nx + ny * ny + nz * nz;
            if (nLenSq < 1e-4f) continue; // Skip nearly degenerate pairs

            float invN = (float) (1.0 / Math.sqrt(nLenSq));
            nx *= invN;
            ny *= invN;
            nz *= invN;

            // Transform tangents via vector transformation (3x3 part of m)
            float t1PrimeX = m[0] * t1x + m[4] * t1y + m[8]  * t1z;
            float t1PrimeY = m[1] * t1x + m[5] * t1y + m[9]  * t1z;
            float t1PrimeZ = m[2] * t1x + m[6] * t1y + m[10] * t1z;

            float t2PrimeX = m[0] * t2x + m[4] * t2y + m[8]  * t2z;
            float t2PrimeY = m[1] * t2x + m[5] * t2y + m[9]  * t2z;
            float t2PrimeZ = m[2] * t2x + m[6] * t2y + m[10] * t2z;

            // Transform normal via FastRenderMath.normalTransformAffine
            float[] nPrime = new float[3];
            FastRenderMath.normalTransformAffine(m, nx, ny, nz, nPrime);

            // Verify that nPrime is unit length (unless degenerate)
            float primeLen = (float) Math.sqrt(nPrime[0] * nPrime[0] + nPrime[1] * nPrime[1] + nPrime[2] * nPrime[2]);
            assertEquals(1.0f, primeLen, 1e-4f, "Transformed normal must be unit length");

            // Verify orthogonality: dot(n', t1') == 0 and dot(n', t2') == 0
            float dot1 = nPrime[0] * t1PrimeX + nPrime[1] * t1PrimeY + nPrime[2] * t1PrimeZ;
            float dot2 = nPrime[0] * t2PrimeX + nPrime[1] * t2PrimeY + nPrime[2] * t2PrimeZ;

            // Normalize dots by tangent magnitude to check angular orthogonality (cos theta)
            float t1Len = (float) Math.sqrt(t1PrimeX * t1PrimeX + t1PrimeY * t1PrimeY + t1PrimeZ * t1PrimeZ);
            float t2Len = (float) Math.sqrt(t2PrimeX * t2PrimeX + t2PrimeY * t2PrimeY + t2PrimeZ * t2PrimeZ);

            float cosTheta1 = Math.abs(dot1) / t1Len;
            float cosTheta2 = Math.abs(dot2) / t2Len;

            assertTrue(cosTheta1 <= ORTHOGONALITY_TOLERANCE,
                "Normal transform broke orthogonality with tangent 1: cos(theta)=" + cosTheta1);
            assertTrue(cosTheta2 <= ORTHOGONALITY_TOLERANCE,
                "Normal transform broke orthogonality with tangent 2: cos(theta)=" + cosTheta2);
        }
    }

    // =========================================================================
    // 5. TransformQuad Equivalence Stress Test
    // =========================================================================

    @Test
    public void testTransformQuadConsistency() {
        Random rand = new Random(0x1337BEEF);
        float[] m = new float[16];
        float[] src = new float[12];
        float[] dst = new float[12];
        float[] expected = new float[3];

        for (int i = 0; i < 16; i++) m[i] = (rand.nextFloat() - 0.5f) * 20.0f;
        for (int i = 0; i < 12; i++) src[i] = (rand.nextFloat() - 0.5f) * 10.0f;

        FastRenderMath.transformQuad(m, src, 0, dst, 0, 3);

        for (int v = 0; v < 4; v++) {
            FastRenderMath.transformVec3f(m, src[v * 3], src[v * 3 + 1], src[v * 3 + 2], expected);
            assertEquals(expected[0], dst[v * 3], EPSILON);
            assertEquals(expected[1], dst[v * 3 + 1], EPSILON);
            assertEquals(expected[2], dst[v * 3 + 2], EPSILON);
        }
    }

    // =========================================================================
    // 6. Zero Heap Allocation Stress Test
    // =========================================================================

    @Test
    public void testZeroAllocationsUnderStress() {
        float[] m = {
            1.5f, 0.2f, 0.0f, 0.0f,
            0.1f, 2.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            5.0f, 6.0f, 7.0f, 1.0f
        };
        float[] vec = new float[3];
        float[] quadSrc = new float[12];
        float[] quadDst = new float[12];
        float dummy = 0.0f;

        // Warm up JIT compiler across all branches (positive, negative, large, small)
        for (int i = 0; i < 200_000; i++) {
            dummy += FastRenderMath.sin((float) (i - 100_000) * 0.0314159f);
            dummy += FastRenderMath.cos((float) (i - 100_000) * 0.0314159f);
            FastRenderMath.transformVec3f(m, (float) (i - 100_000), 2.0f, 3.0f, vec);
            FastRenderMath.normalTransformOrthogonal(m, 0.0f, 1.0f, 0.0f, vec);
            FastRenderMath.normalTransformAffine(m, 0.0f, 1.0f, 0.0f, vec);
            FastRenderMath.transformQuad(m, quadSrc, 0, quadDst, 0, 3);
        }

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (threadBean instanceof com.sun.management.ThreadMXBean sunBean && sunBean.isThreadAllocatedMemorySupported()) {
            sunBean.setThreadAllocatedMemoryEnabled(true);
            long before = sunBean.getCurrentThreadAllocatedBytes();

            int iterations = 200_000;
            for (int i = 0; i < iterations; i++) {
                dummy += FastRenderMath.sin((float) (i - 100_000) * 0.0314159f);
                dummy += FastRenderMath.cos((float) (i - 100_000) * 0.0314159f);
                FastRenderMath.transformVec3f(m, (float) i, 2.0f, 3.0f, vec);
                FastRenderMath.normalTransformOrthogonal(m, 0.0f, 1.0f, 0.0f, vec);
                FastRenderMath.normalTransformAffine(m, 0.0f, 1.0f, 0.0f, vec);
                FastRenderMath.transformQuad(m, quadSrc, 0, quadDst, 0, 3);
            }

            long after = sunBean.getCurrentThreadAllocatedBytes();
            long allocated = after - before;

            System.out.printf("[FastRenderMathStressTest] Heap bytes allocated during %d iterations: %d bytes%n", iterations, allocated);
            assertEquals(0, allocated, "Hot math operations must allocate exactly 0 heap bytes, allocated: " + allocated);
        }

        assertNotEquals(Float.NaN, dummy);
    }

    // =========================================================================
    // 7. Offset and Stride Verification (Minecraft Vertex Buffer layout)
    // =========================================================================

    @Test
    public void testStrideAndOffsetVertexBuffers() {
        // Minecraft standard vertex format has 8 floats (32 bytes) per vertex:
        // [X, Y, Z, ColorPacked, U, V, LightOverlayPacked, NormalPacked]
        int stride = 8;
        float[] src = new float[stride * 4];
        float[] dst = new float[stride * 4];

        // Fill with dummy data
        for (int v = 0; v < 4; v++) {
            src[v * stride]     = (float) (v + 1);       // X
            src[v * stride + 1] = (float) (v + 2) * 2;   // Y
            src[v * stride + 2] = (float) (v + 3) * 3;   // Z
            src[v * stride + 3] = 0.5f;                  // Dummy
            src[v * stride + 4] = 0.25f;                 // U
            src[v * stride + 5] = 0.75f;                 // V
            src[v * stride + 6] = 100.0f;                // Light
            src[v * stride + 7] = 200.0f;                // Normal
        }

        // Fill dst with sentinel markers
        for (int i = 0; i < dst.length; i++) dst[i] = -999.0f;

        float[] m = {
            2, 0, 0, 0,
            0, 3, 0, 0,
            0, 0, 4, 0,
            10, 20, 30, 1
        };

        FastRenderMath.transformQuad(m, src, 0, dst, 0, stride);

        for (int v = 0; v < 4; v++) {
            float expX = 2 * src[v * stride] + 10;
            float expY = 3 * src[v * stride + 1] + 20;
            float expZ = 4 * src[v * stride + 2] + 30;

            assertEquals(expX, dst[v * stride], EPSILON, "X mismatch at vertex " + v);
            assertEquals(expY, dst[v * stride + 1], EPSILON, "Y mismatch at vertex " + v);
            assertEquals(expZ, dst[v * stride + 2], EPSILON, "Z mismatch at vertex " + v);

            // Ensure other attributes in dst are untouched
            for (int attr = 3; attr < stride; attr++) {
                assertEquals(-999.0f, dst[v * stride + attr], "Untouched vertex attribute was corrupted at v=" + v + " attr=" + attr);
            }
        }
    }

    @Test
    public void testExtremeLargeAnglesSafety() {
        // Values far beyond 1000pi should not throw any exception or crash the JVM
        float[] extremeAngles = {
            100_000.0f,
            -100_000.0f,
            1_000_000.0f,
            -1_000_000.0f,
            Float.MAX_VALUE,
            -Float.MAX_VALUE
        };

        for (float rad : extremeAngles) {
            float s = FastRenderMath.sin(rad);
            float c = FastRenderMath.cos(rad);
            assertFalse(Float.isInfinite(s), "sin must not return infinite: " + s);
            assertFalse(Float.isInfinite(c), "cos must not return infinite: " + c);
        }
    }

    @Test
    public void testArrayOffsetsSafety() {
        float[] m = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1 };
        float[] buffer = new float[10];

        // Transform into offset 5
        FastRenderMath.transformVec3f(m, 1.0f, 2.0f, 3.0f, buffer, 5);
        assertEquals(1.0f, buffer[5], EPSILON);
        assertEquals(2.0f, buffer[6], EPSILON);
        assertEquals(3.0f, buffer[7], EPSILON);

        // Normal transform into offset 2
        FastRenderMath.normalTransformOrthogonal(m, 0.0f, 1.0f, 0.0f, buffer, 2);
        assertEquals(0.0f, buffer[2], EPSILON);
        assertEquals(1.0f, buffer[3], EPSILON);
        assertEquals(0.0f, buffer[4], EPSILON);

        FastRenderMath.normalTransformAffine(m, 0.0f, 0.0f, 1.0f, buffer, 2);
        assertEquals(0.0f, buffer[2], EPSILON);
        assertEquals(0.0f, buffer[3], EPSILON);
        assertEquals(1.0f, buffer[4], EPSILON);
    }
}
