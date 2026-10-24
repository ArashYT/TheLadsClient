package com.thelads.core;

import com.thelads.core.client.DropConfirmationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DropConfirmationTest {
    @BeforeEach
    void setUp() {
        DropConfirmationHelper.reset();
    }

    @Test
    void testNonValuableDropsImmediately() {
        assertTrue(DropConfirmationHelper.checkCanDrop(1000L, 1500L, false, true));
    }

    @Test
    void testDisabledDropsImmediately() {
        assertTrue(DropConfirmationHelper.checkCanDrop(1000L, 1500L, true, false));
    }

    @Test
    void testFirstPressArmsConfirmation() {
        assertFalse(DropConfirmationHelper.checkCanDrop(1000L, 1500L, true, true));
    }

    @Test
    void testSecondPressWithinWindowConfirms() {
        assertFalse(DropConfirmationHelper.checkCanDrop(1000L, 1500L, true, true));
        assertTrue(DropConfirmationHelper.checkCanDrop(1800L, 1500L, true, true));
    }

    @Test
    void testSecondPressAfterWindowExpiresRearms() {
        assertFalse(DropConfirmationHelper.checkCanDrop(1000L, 1500L, true, true));
        assertFalse(DropConfirmationHelper.checkCanDrop(3000L, 1500L, true, true));
    }

    @Test
    void testValuableNames() {
        assertTrue(DropConfirmationHelper.isValuableName("item.diamondSword"));
        assertTrue(DropConfirmationHelper.isValuableName("item.netheritePickaxe"));
        assertTrue(DropConfirmationHelper.isValuableName("item.elytra"));
        assertFalse(DropConfirmationHelper.isValuableName("item.dirt"));
        assertFalse(DropConfirmationHelper.isValuableName("item.cobblestone"));
    }
}
