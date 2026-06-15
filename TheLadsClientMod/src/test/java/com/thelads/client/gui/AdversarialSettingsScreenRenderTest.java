package com.thelads.client.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;

import org.mockito.Mockito;
import java.lang.reflect.Field;

public class AdversarialSettingsScreenRenderTest {

    @org.junit.jupiter.api.BeforeEach
    public void setup() throws Exception {
        com.thelads.client.e2e.ReflectionHelper.setupMocks();
    }

    @org.junit.jupiter.api.AfterEach
    public void teardown() {
        com.thelads.client.e2e.ReflectionHelper.teardownMocks();
    }

    @Test
    public void testExtractRenderState() throws Exception {
        Screen mockParent = Mockito.mock(Screen.class);
        SettingsScreen screen = new SettingsScreen(mockParent);
        
        // Mock the font to avoid null pointer
        Font mockFont = Mockito.mock(Font.class);
        Field fontField = Screen.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(screen, mockFont);
        
        Field widthField = Screen.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.set(screen, 800);
        
        Field heightField = Screen.class.getDeclaredField("height");
        heightField.setAccessible(true);
        heightField.set(screen, 600);

        GuiGraphicsExtractor mockExtractor = Mockito.mock(GuiGraphicsExtractor.class);

        assertDoesNotThrow(() -> {
            screen.extractRenderState(mockExtractor, 100, 100, 1.0f);
        });

        // Verify that the background was filled
        Mockito.verify(mockExtractor).fill(0, 0, 800, 600, 0x80000000);
        
        // Verify that the title was drawn
        Mockito.verify(mockExtractor).centeredText(Mockito.eq(mockFont), Mockito.any(Component.class), Mockito.eq(400), Mockito.eq(20), Mockito.eq(0xFFFFFF));
    }
}
