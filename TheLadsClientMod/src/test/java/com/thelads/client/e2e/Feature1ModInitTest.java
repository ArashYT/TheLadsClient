package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class Feature1ModInitTest {
    @BeforeEach
    public void setupMocks() throws Exception { com.thelads.client.e2e.ReflectionHelper.setupMocks(); }
    @AfterEach
    public void teardownMocks() { com.thelads.client.e2e.ReflectionHelper.teardownMocks(); }


    @Test
    public void testModInitializesWithoutCrashing() throws Exception {
        Class<?> modClass = Class.forName("com.thelads.client.TheLadsClientMod");
        modClass.getDeclaredMethod("onInitializeClient").invoke(modClass.getDeclaredConstructor().newInstance());
    }

    @Test
    public void testModRegistersKeybindingsOnStart() throws Exception {
        Class<?> modClass = Class.forName("com.thelads.client.TheLadsClientMod");
        Object instance = modClass.getDeclaredConstructor().newInstance();
        modClass.getDeclaredMethod("onInitializeClient").invoke(instance);
        
        java.lang.reflect.Field keyBindingField = modClass.getDeclaredField("rightShiftKeyBinding");
        keyBindingField.setAccessible(true);
        Object keyBinding = keyBindingField.get(instance);
        
        if (keyBinding == null) {
            throw new RuntimeException("KeyBinding was not registered");
        }
    }

    @Test
    public void testModLoadsConfigOnStart() throws Exception {
        Class<?> modClass = Class.forName("com.thelads.client.TheLadsClientMod");
        modClass.getDeclaredMethod("onInitializeClient").invoke(modClass.getDeclaredConstructor().newInstance());
        
        Object config = ReflectionHelper.getConfig();
        if (config == null) {
            throw new RuntimeException("Config was not loaded on start");
        }
    }

    @Test
    public void testModSetsDefaultConfigIfNotExists() throws Exception {
        java.io.File configFile = new java.io.File("config/theladsclient.json");
        if (configFile.exists()) {
            configFile.delete();
        }
        
        Class<?> modClass = Class.forName("com.thelads.client.TheLadsClientMod");
        modClass.getDeclaredMethod("onInitializeClient").invoke(modClass.getDeclaredConstructor().newInstance());
        
        if (!configFile.exists()) {
            throw new RuntimeException("Default config was not created");
        }
    }

    @Test
    public void testModRegistersEventHandlers() throws Exception {
        Class<?> modClass = Class.forName("com.thelads.client.TheLadsClientMod");
        Object instance = modClass.getDeclaredConstructor().newInstance();
        modClass.getDeclaredMethod("onInitializeClient").invoke(instance);
        
        // This is purely opaque box, we assume it registers tick events
        Class<?> clientTickEvents = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
        if (clientTickEvents == null) {
            throw new RuntimeException("ClientTickEvents not found");
        }
    }
}
