package com.thelads.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import org.mockito.Mockito;
import org.mockito.MockedStatic;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import java.lang.reflect.Field;

public class AdversarialModInitFallbackTest {

    private MockedStatic<KeyMappingHelper> mockKeyMappingHelper;

    @BeforeEach
    public void setupMocks() throws Exception { 
        // We do NOT use ReflectionHelper.setupMocks() completely because we need custom KeyMappingHelper mock
        // ReflectionHelper.setupMocks() sets up a mock that returns the argument
        
        mockKeyMappingHelper = Mockito.mockStatic(KeyMappingHelper.class);
        mockKeyMappingHelper.when(() -> KeyMappingHelper.registerKeyMapping(Mockito.any())).thenReturn(null);
    }
    
    @AfterEach
    public void teardownMocks() { 
        if (mockKeyMappingHelper != null) {
            mockKeyMappingHelper.close();
            mockKeyMappingHelper = null;
        }
    }

    @Test
    public void testModInitKeyBindingFallback() throws Exception {
        TheLadsClientMod mod = new TheLadsClientMod();
        assertDoesNotThrow(() -> {
            mod.onInitializeClient();
        });
        
        Field keyBindingField = TheLadsClientMod.class.getDeclaredField("rightShiftKeyBinding");
        keyBindingField.setAccessible(true);
        KeyMapping keyBinding = (KeyMapping) keyBindingField.get(null);
        
        assertNotNull(keyBinding, "rightShiftKeyBinding should not be null, the fallback should have set it to 'temp'");
        assertEquals("key.theladsclient.settings", keyBinding.getName());
    }
}
