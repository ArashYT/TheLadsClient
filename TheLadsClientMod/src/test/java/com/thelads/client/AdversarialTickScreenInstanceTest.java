package com.thelads.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.gui.screens.Screen;
import com.thelads.client.gui.SettingsScreen;

public class AdversarialTickScreenInstanceTest {

    @BeforeEach
    public void setupMocks() throws Exception { 
        com.thelads.client.e2e.ReflectionHelper.setupMocks(); 
    }
    
    @AfterEach
    public void teardownMocks() { 
        com.thelads.client.e2e.ReflectionHelper.teardownMocks(); 
    }

    @Test
    public void testTickWithSettingsScreenAlreadyOpen() throws Exception {
        com.thelads.client.TheLadsClientMod mod = new com.thelads.client.TheLadsClientMod();
        mod.onInitializeClient();
        
        // Open the screen once
        com.thelads.client.e2e.ReflectionHelper.simulateRightShiftKeyPress();
        
        Object screen1 = com.thelads.client.e2e.ReflectionHelper.getCurrentScreen();
        assertTrue(screen1 instanceof SettingsScreen);
        
        // Simulate pressing again while already open
        com.thelads.client.e2e.ReflectionHelper.simulateRightShiftKeyPress();
        
        Object screen2 = com.thelads.client.e2e.ReflectionHelper.getCurrentScreen();
        
        // It should be the EXACT same instance, it shouldn't have created a new one
        assertSame(screen1, screen2, "Pressing Right Shift while SettingsScreen is open should not create a new screen");
    }
}
