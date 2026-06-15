package com.thelads.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public class AdversarialTickNullClientTest {

    @BeforeEach
    public void setupMocks() throws Exception { 
        com.thelads.client.e2e.ReflectionHelper.setupMocks(); 
    }
    
    @AfterEach
    public void teardownMocks() { 
        com.thelads.client.e2e.ReflectionHelper.teardownMocks(); 
    }

    @Test
    public void testTickWithNullClient() throws Exception {
        // Initialize mod to register the event
        TheLadsClientMod mod = new TheLadsClientMod();
        mod.onInitializeClient();
        
        // Ensure keybinding returns true for consumeClick
        java.lang.reflect.Field kbFieldMain = TheLadsClientMod.class.getDeclaredField("rightShiftKeyBinding");
        kbFieldMain.setAccessible(true);
        KeyMapping keyBinding = (KeyMapping) kbFieldMain.get(null);
        
        // We simulate a click by manually calling click
        Class<?> inputConstantsKeyClass = Class.forName("com.mojang.blaze3d.platform.InputConstants$Key");
        Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
        int rightShift = glfwClass.getField("GLFW_KEY_RIGHT_SHIFT").getInt(null);
        Class<?> inputConstantsTypeClass = Class.forName("com.mojang.blaze3d.platform.InputConstants$Type");
        java.lang.reflect.Field keysymField = inputConstantsTypeClass.getField("KEYSYM");
        Object keysym = keysymField.get(null);
        Method getOrCreate = keysym.getClass().getMethod("getOrCreate", int.class);
        Object key = getOrCreate.invoke(keysym, rightShift);
        
        Method clickMethod = KeyMapping.class.getMethod("click", inputConstantsKeyClass);
        clickMethod.invoke(null, key);
        
        // Invoke tick event with NULL client
        java.lang.reflect.Field endClientTickField = ClientTickEvents.class.getDeclaredField("END_CLIENT_TICK");
        endClientTickField.setAccessible(true);
        Object event = endClientTickField.get(null);
        Method invokerMethod = event.getClass().getMethod("invoker");
        Object invoker = invokerMethod.invoke(event);
        Class<?> clientTickEventClass = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick");
        Method onEndTickMethod = clientTickEventClass.getMethod("onEndTick", Class.forName("net.minecraft.client.Minecraft"));
        
        assertDoesNotThrow(() -> {
            onEndTickMethod.invoke(invoker, new Object[]{null});
        });
    }
}
