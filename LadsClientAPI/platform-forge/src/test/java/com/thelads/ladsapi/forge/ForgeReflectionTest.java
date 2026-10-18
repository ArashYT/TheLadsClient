package com.thelads.ladsapi.forge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ForgeReflectionTest {

    @Test
    public void testNullSafety() {
        assertDoesNotThrow(() -> {
            assertNull(ForgeReflection.getGameProfile(null));
            assertNull(ForgeReflection.getPlayerUUID(null));
            assertEquals("Unknown", ForgeReflection.getPlayerName(null));
            assertEquals("minecraft:overworld", ForgeReflection.getWorldName(null));
            assertFalse(ForgeReflection.hasPermissions(null, 2));
            assertFalse(ForgeReflection.hasPermission(null, 2));
            ForgeReflection.disconnect(null, "Test disconnect");
        });
    }

    @Test
    public void testPlayerClassAvailableInCompileClasspath() {
        assertNotNull(net.minecraft.world.entity.player.Player.class);
        assertNotNull(net.minecraft.server.level.ServerPlayer.class);
        assertNotNull(net.minecraft.commands.CommandSourceStack.class);
    }
}
