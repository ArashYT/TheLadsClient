package com.thelads.client.e2e;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

public class Feature2GuiTest {
    @BeforeEach
    public void setupMocks() throws Exception { com.thelads.client.e2e.ReflectionHelper.setupMocks(); }
    @AfterEach
    public void teardownMocks() { com.thelads.client.e2e.ReflectionHelper.teardownMocks(); }


    @Test
    public void testRightShiftOpensGui() throws Exception {
        ReflectionHelper.simulateRightShiftKeyPress();
        Object currentScreen = ReflectionHelper.getCurrentScreen();
        if (currentScreen == null || !currentScreen.getClass().getName().contains("SettingsScreen")) {
            throw new RuntimeException("GUI did not open on Right-Shift");
        }
    }

    @Test
    public void testOtherKeyDoesNotOpenGui() throws Exception {
        // Initialize mod if not initialized
        Class<?> modClassMain = Class.forName("com.thelads.client.TheLadsClientMod");
        java.lang.reflect.Field kbFieldMain = modClassMain.getDeclaredField("rightShiftKeyBinding");
        kbFieldMain.setAccessible(true);
        if (kbFieldMain.get(null) == null) {
            modClassMain.getDeclaredMethod("onInitializeClient").invoke(modClassMain.getDeclaredConstructor().newInstance());
        }


        // Simulate arbitrary key press, like space
        Class<?> keyBindingClass = Class.forName("net.minecraft.client.KeyMapping");
        Class<?> keyClass = Class.forName("com.mojang.blaze3d.platform.InputConstants$Key");
        java.lang.reflect.Method onKeyPressedMethod = keyBindingClass.getDeclaredMethod("click", keyClass);
        onKeyPressedMethod.setAccessible(true);
        
        Class<?> typeClass = Class.forName("com.mojang.blaze3d.platform.InputConstants$Type");
        java.lang.reflect.Field keyboardField = typeClass.getDeclaredField("KEYSYM");
        keyboardField.setAccessible(true);
        Object keysym = keyboardField.get(null);
        
        java.lang.reflect.Method createFromCodeMethod = typeClass.getDeclaredMethod("getOrCreate", int.class);
        createFromCodeMethod.setAccessible(true);
        Object spaceKey = createFromCodeMethod.invoke(keysym, 32); // Space
        
        onKeyPressedMethod.invoke(null, spaceKey);
        
        Class<?> clientTickEventsClass = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
        java.lang.reflect.Field endClientTickField = clientTickEventsClass.getDeclaredField("END_CLIENT_TICK");
        endClientTickField.setAccessible(true);
        Object event = endClientTickField.get(null);
        java.lang.reflect.Method invokerMethod = event.getClass().getMethod("invoker");
        Object invoker = invokerMethod.invoke(event);
        Class<?> clientTickEventClass = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick");
        java.lang.reflect.Method onEndTickMethod = clientTickEventClass.getMethod("onEndTick", Class.forName("net.minecraft.client.Minecraft"));
        
        Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
        java.lang.reflect.Method getInstance = mcClass.getDeclaredMethod("getInstance");
        Object mc = getInstance.invoke(null);
        onEndTickMethod.invoke(invoker, mc);
        
        Object currentScreen = ReflectionHelper.getCurrentScreen();
        if (currentScreen != null && currentScreen.getClass().getName().contains("SettingsScreen")) {
            throw new RuntimeException("GUI opened on incorrect key");
        }
    }

    @Test
    public void testGuiContainsCapeWidget() throws Exception {
        ReflectionHelper.simulateRightShiftKeyPress();
        Object currentScreen = ReflectionHelper.getCurrentScreen();
        if (!ReflectionHelper.hasWidgetWithText(currentScreen, "Cape")) {
            throw new RuntimeException("GUI missing Cape widget");
        }
    }

    @Test
    public void testGuiContainsScaleWidget() throws Exception {
        ReflectionHelper.simulateRightShiftKeyPress();
        Object currentScreen = ReflectionHelper.getCurrentScreen();
        if (!ReflectionHelper.hasWidgetWithText(currentScreen, "Scale")) {
            throw new RuntimeException("GUI missing UI Scaling widget");
        }
    }

    @Test
    public void testClosingGuiSavesConfig() throws Exception {
        ReflectionHelper.simulateRightShiftKeyPress();
        Object currentScreen = ReflectionHelper.getCurrentScreen();
        if (currentScreen != null) {
            java.lang.reflect.Method closeMethod = currentScreen.getClass().getMethod("onClose");
            closeMethod.setAccessible(true);
            closeMethod.invoke(currentScreen);
        }
        
        java.io.File configFile = new java.io.File("config/theladsclient.json");
        if (!configFile.exists() || configFile.lastModified() < System.currentTimeMillis() - 5000) {
            throw new RuntimeException("Config was not saved after closing GUI");
        }
    }
}
