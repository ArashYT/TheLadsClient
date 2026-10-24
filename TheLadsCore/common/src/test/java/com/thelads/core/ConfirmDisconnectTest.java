package com.thelads.core;

import com.thelads.core.client.ConfirmDisconnectHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConfirmDisconnectTest {
    @BeforeEach
    void setUp() {
        ConfirmDisconnectHelper.reset();
    }

    @Test
    void testConfirmDisconnectSingleplayer() {
        // In singleplayer, should disconnect immediately
        assertTrue(ConfirmDisconnectHelper.onClickDisconnect(1000L, 3000L, false, true));
    }

    @Test
    void testConfirmDisconnectDisabled() {
        // If disabled, should disconnect immediately even in multiplayer
        assertTrue(ConfirmDisconnectHelper.onClickDisconnect(1000L, 3000L, true, false));
    }

    @Test
    void testConfirmDisconnectMultiplayerFlow() {
        long now = 10000L;
        // First click: does not disconnect, arms confirmation
        assertFalse(ConfirmDisconnectHelper.onClickDisconnect(now, 3000L, true, true));
        assertTrue(ConfirmDisconnectHelper.isConfirming(now + 1000L));

        // Second click within 3000ms: disconnects!
        assertTrue(ConfirmDisconnectHelper.onClickDisconnect(now + 1500L, 3000L, true, true));
        assertFalse(ConfirmDisconnectHelper.isConfirming(now + 1500L));
    }

    @Test
    void testConfirmDisconnectTimeout() {
        long now = 10000L;
        assertFalse(ConfirmDisconnectHelper.onClickDisconnect(now, 3000L, true, true));
        // After timeout: confirmation expired
        assertFalse(ConfirmDisconnectHelper.isConfirming(now + 3500L));
        // Next click rearms confirmation rather than disconnecting
        assertFalse(ConfirmDisconnectHelper.onClickDisconnect(now + 3500L, 3000L, true, true));
    }
}
