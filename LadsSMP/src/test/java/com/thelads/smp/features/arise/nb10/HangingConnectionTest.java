package com.thelads.smp.features.arise.nb10;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HangingConnectionTest {

    @Test
    void testConnectionCreationAndDefaults() {
        UUID id = UUID.randomUUID();
        BlockPos target = new BlockPos(12, 64, -8);
        HangingConnection conn = new HangingConnection(
                id,
                target,
                Direction.NORTH,
                DecorationType.LIGHTS,
                HangingPalettes.WARM_WHITE,
                1.0F,
                null
        );

        assertEquals(id, conn.getId());
        assertEquals(target, conn.getTargetPos());
        assertEquals(Direction.NORTH, conn.getTargetFace());
        assertEquals(DecorationType.LIGHTS, conn.getType());
        assertArrayEquals(HangingPalettes.WARM_WHITE, conn.getColors());
        assertEquals(1.0F, conn.getSlack(), 0.001F);
    }

    @Test
    void testRainbowPalette() {
        assertEquals(8, HangingPalettes.RAINBOW.length, "Rainbow should contain 8 vibrant hues");
    }

    @Test
    void testDecorationTypeByName() {
        assertEquals(DecorationType.LIGHTS, DecorationType.byName("lights"));
        assertEquals(DecorationType.PENNANT, DecorationType.byName("pennant"));
        assertEquals(DecorationType.GARLAND, DecorationType.byName("garland"));
        assertEquals(DecorationType.TINSEL, DecorationType.byName("tinsel"));
        assertEquals(DecorationType.LIGHTS, DecorationType.byName("unknown_fallback"));
    }
}
