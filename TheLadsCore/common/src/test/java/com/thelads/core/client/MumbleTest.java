package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thelads.core.client.media.MumbleLink;
import org.junit.jupiter.api.Test;

class MumbleTest {

    @Test
    void mumbleVectorCalculation() {
        // Yaw 0 (facing south in Minecraft: -Z is north, +Z is south), Pitch 0 (horizon)
        float[] front = MumbleLink.calculateFrontVector(0f, 0f);

        assertEquals(0.0f, front[0], 1e-4);
        assertEquals(0.0f, front[1], 1e-4);
        assertEquals(1.0f, front[2], 1e-4);

        // Vector magnitude is 1.0 (unit vector)
        double mag = Math.sqrt(front[0] * front[0] + front[1] * front[1] + front[2] * front[2]);
        assertEquals(1.0, mag, 1e-4);
    }
}
