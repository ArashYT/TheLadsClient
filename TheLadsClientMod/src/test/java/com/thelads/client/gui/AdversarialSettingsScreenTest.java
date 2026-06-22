package com.thelads.client.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import com.thelads.client.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;

import org.mockito.Mockito;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class AdversarialSettingsScreenTest {

    @BeforeEach
    public void setupMocks() throws Exception { 
        com.thelads.client.e2e.ReflectionHelper.setupMocks(); 
        ConfigManager.getConfig().setUiScalingEnabled(false);
    }
    
    @AfterEach
    public void teardownMocks() { 
        com.thelads.client.e2e.ReflectionHelper.teardownMocks(); 
    }

    @Test
    public void testSettingsScreenForcesRefreshOnGuiScaleChange() throws Exception {
        Minecraft mockMc = Mockito.mock(Minecraft.class);
        Screen mockParent = Mockito.mock(Screen.class);
        
        SettingsScreen screen = new SettingsScreen(mockParent);
        
        // Setup fields
        Field mcField = Screen.class.getDeclaredField("minecraft");
        mcField.setAccessible(true);
        mcField.set(screen, mockMc);
        
        Field widthField = Screen.class.getDeclaredField("width");
        widthField.setAccessible(true);
        widthField.set(screen, 800);
        
        Field heightField = Screen.class.getDeclaredField("height");
        heightField.setAccessible(true);
        heightField.set(screen, 600);
        
        // Call init() using reflection to access protected method
        Method initMethod = SettingsScreen.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(screen);
        
        // Find the renderables list to get the buttons
        List<?> renderables = null;
        for (Field f : Screen.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                List<?> list = (List<?>) f.get(screen);
                if (list != null && !list.isEmpty() && list.get(0) instanceof Button) {
                    renderables = list;
                    break;
                }
            }
        }
        
        assertNotNull(renderables, "Could not find renderables list in Screen");
        assertTrue(renderables.size() >= 2, "Expected at least 2 buttons (Cape and Scale)");
        
        Button scaleButton = null;
        for (Object obj : renderables) {
            Button btn = (Button) obj;
            if (btn.getMessage().getString().contains("Scale")) {
                scaleButton = btn;
                break;
            }
        }
        
        assertNotNull(scaleButton, "Could not find Scale button");
        
        // Clear any interactions that might have happened during init
        Mockito.clearInvocations(mockMc);
        
        // Press the Scale button using reflection to bypass signature issues
        for (Method m : Button.class.getMethods()) {
            if (m.getName().equals("onPress")) {
                m.setAccessible(true);
                if (m.getParameterCount() == 0) {
                    m.invoke(scaleButton);
                } else if (m.getParameterCount() == 1) {
                    // Try passing null for InputWithModifiers or whatever it is
                    m.invoke(scaleButton, new Object[]{null});
                }
            }
        }
        
        // Verify that the UI scale was enabled in config
        assertTrue(ConfigManager.getConfig().isUiScalingEnabled(), "Config was not updated");
        
        // The gap: the screen doesn't notify Minecraft to update the resolution/GUI scale!
        boolean hasInteractions = Mockito.mockingDetails(mockMc).getInvocations().size() > 0;
        assertTrue(hasInteractions, "SettingsScreen did not notify Minecraft to refresh the GUI scale immediately! This is a facade!");
    }
}
