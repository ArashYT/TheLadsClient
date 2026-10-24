package com.thelads.core.client.server;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerReorderHelperTest {
    @BeforeEach
    void setUp() {
        ServerReorderHelper.cancelDrag();
    }

    @Test
    void testDragCalculation() {
        assertFalse(ServerReorderHelper.isDragging());
        ServerReorderHelper.startDrag(1, 100);
        assertTrue(ServerReorderHelper.isDragging());
        assertEquals(1, ServerReorderHelper.getDraggedIndex());

        // Top = 32, slotHeight = 36, 5 slots
        int target = ServerReorderHelper.updateDrag(32 + 36 * 3 + 10, 32, 36, 5);
        assertEquals(3, target);
        assertEquals(3, ServerReorderHelper.getTargetIndex());
    }

    @Test
    void testReorderList() {
        List<String> servers = new ArrayList<>(List.of("Server A", "Server B", "Server C", "Server D"));
        ServerReorderHelper.startDrag(0, 50);
        ServerReorderHelper.updateDrag(50 + 72, 50, 36, 4); // slot 2 ("Server C")
        assertEquals(2, ServerReorderHelper.getTargetIndex());

        boolean ok = ServerReorderHelper.applyReorder(servers);
        assertTrue(ok);
        assertFalse(ServerReorderHelper.isDragging());
        assertEquals(List.of("Server B", "Server C", "Server A", "Server D"), servers);
    }
}
