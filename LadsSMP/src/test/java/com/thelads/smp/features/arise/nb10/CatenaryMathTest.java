package com.thelads.smp.features.arise.nb10;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CatenaryMathTest {

    @Test
    void testEndpointsPreservation() {
        Vec3 p0 = new Vec3(10.0, 64.0, 20.0);
        Vec3 p1 = new Vec3(25.0, 70.0, 35.0);
        float slack = 1.0F;

        Vec3 atStart = CatenaryMath.getPoint(p0, p1, slack, 0.0F);
        assertEquals(p0.x, atStart.x, 0.001);
        assertEquals(p0.y, atStart.y, 0.001);
        assertEquals(p0.z, atStart.z, 0.001);

        Vec3 atEnd = CatenaryMath.getPoint(p0, p1, slack, 1.0F);
        assertEquals(p1.x, atEnd.x, 0.001);
        assertEquals(p1.y, atEnd.y, 0.001);
        assertEquals(p1.z, atEnd.z, 0.001);
    }

    @Test
    void testSagDepthCalculation() {
        float sag10 = CatenaryMath.getSagDepth(10.0F, 1.0F);
        assertTrue(sag10 >= 0.8F && sag10 <= 1.0F, "Sag for 10 blocks span should be ~0.85");

        float sagShort = CatenaryMath.getSagDepth(0.5F, 1.0F);
        assertTrue(sagShort >= 0.12F, "Short spans should clamp to minimum sag");

        float sagLong = CatenaryMath.getSagDepth(50.0F, 1.0F);
        assertTrue(sagLong <= 2.8F, "Long spans should clamp to maximum sag");
    }

    @Test
    void testMidpointSag() {
        Vec3 p0 = new Vec3(0.0, 64.0, 0.0);
        Vec3 p1 = new Vec3(10.0, 64.0, 0.0);
        float slack = 1.0F;

        Vec3 mid = CatenaryMath.getPoint(p0, p1, slack, 0.5F);
        assertEquals(5.0, mid.x, 0.001);
        assertEquals(0.0, mid.z, 0.001);

        float expectedSag = CatenaryMath.getSagDepth(10.0F, slack);
        assertEquals(64.0 - expectedSag, mid.y, 0.001, "Midpoint Y must equal baseline minus sag depth");
    }

    @Test
    void testWindSwayPreservesEndpoints() {
        Vec3 p0 = new Vec3(5.0, 65.0, 5.0);
        Vec3 p1 = new Vec3(20.0, 65.0, 20.0);
        float slack = 1.0F;

        for (float time = 0.0F; time < 100.0F; time += 15.0F) {
            Vec3 startSway = CatenaryMath.getPointWithWind(p0, p1, slack, 0.0F, time, 0.0F);
            assertEquals(p0.x, startSway.x, 0.001);
            assertEquals(p0.y, startSway.y, 0.001);
            assertEquals(p0.z, startSway.z, 0.001);

            Vec3 endSway = CatenaryMath.getPointWithWind(p0, p1, slack, 1.0F, time, 0.0F);
            assertEquals(p1.x, endSway.x, 0.001);
            assertEquals(p1.y, endSway.y, 0.001);
            assertEquals(p1.z, endSway.z, 0.001);
        }
    }

    @Test
    void testComputeCurvePointsBufferPopulation() {
        int segs = 16;
        float[] x = new float[segs + 1];
        float[] y = new float[segs + 1];
        float[] z = new float[segs + 1];

        CatenaryMath.computeCurvePoints(0, 10, 0, 16, 10, 0, 1.0F, segs, x, y, z);

        assertEquals(0.0F, x[0], 0.001F);
        assertEquals(10.0F, y[0], 0.001F);
        assertEquals(0.0F, z[0], 0.001F);

        assertEquals(16.0F, x[segs], 0.001F);
        assertEquals(10.0F, y[segs], 0.001F);
        assertEquals(0.0F, z[segs], 0.001F);

        // Midpoint should be lower than endpoints
        assertTrue(y[segs / 2] < 10.0F, "Midpoint must have catenary sag downwards");
    }
}
