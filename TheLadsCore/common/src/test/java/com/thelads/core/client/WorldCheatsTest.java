package com.thelads.core.client;

import gg.essential.Essential;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class WorldCheatsTest {
    @Test
    void theHostsChoiceForJoinedPlayersStaysWithTheWorld(@TempDir Path world) {
        assertNull(WorldCheats.guests(world), "never chosen");
        WorldCheats.guests(world, true);
        assertEquals(Boolean.TRUE, WorldCheats.guests(world));
        WorldCheats.guests(world, false);
        assertEquals(Boolean.FALSE, WorldCheats.guests(world));
    }

    @Test
    void essentialsSwitchTakesTheWorldsValue() {
        Essential.WorldManager world = Essential.INSTANCE.world;
        assertEquals(Boolean.FALSE, WorldCheats.essential(), "Essential's own switch is read");
        assertTrue(WorldCheats.essential(true));
        assertTrue(world.settings.getCheats(), "its world settings take the world's switch");
        assertEquals("Creative", world.settings.getGameMode());
        assertTrue(world.settings.getDifficultyLocked());
        assertEquals(Set.of("friend"), world.settings.getOps(), "and keep everything else");
        assertEquals(Boolean.TRUE, Essential.INSTANCE.manager.applied, "26.x: what it answers changes at once");
        assertEquals(Boolean.TRUE, WorldCheats.essential());
    }
}
