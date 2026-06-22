package com.thelads.client.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.gui.screens.Screen;

import org.mockito.Mockito;

public class AdversarialSettingsScreenCloseTest {

    @BeforeEach
    public void setupMocks() throws Exception { 
        com.thelads.client.e2e.ReflectionHelper.setupMocks(); 
    }
    
    @AfterEach
    public void teardownMocks() { 
        com.thelads.client.e2e.ReflectionHelper.teardownMocks(); 
    }

    @Test
    public void testOnCloseWithNullMinecraft() {
        Screen mockParent = Mockito.mock(Screen.class);
        SettingsScreen screen = new SettingsScreen(mockParent);
        
        // this.minecraft is null by default in Screen
        assertDoesNotThrow(() -> {
            screen.onClose();
        });
    }
}
