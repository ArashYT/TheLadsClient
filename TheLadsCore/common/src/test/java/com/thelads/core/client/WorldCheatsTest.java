package com.thelads.core.client;

import java.nio.file.Path;
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
}
