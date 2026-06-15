package com.thelads.client.e2e;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.gui.screens.Screen;

public class ReflectionHelper {

    private static MockedStatic<Minecraft> mockedMinecraft;
    private static MockedStatic<FabricLoader> mockedFabricLoader;
    private static MockedStatic<KeyMappingHelper> mockedKeyMappingHelper;
    private static Minecraft mcMock;
    private static FabricLoader loaderMock;

    public static synchronized void setupMocks() {
        if (mockedMinecraft == null) {
            mockedMinecraft = Mockito.mockStatic(Minecraft.class);
            mcMock = Mockito.mock(Minecraft.class);
            mockedMinecraft.when(Minecraft::getInstance).thenReturn(mcMock);
            
            // Mock setScreen to update the screen field in the mock and call init
            Mockito.doAnswer(invocation -> {
                Screen screen = invocation.getArgument(0);
                mcMock.screen = screen;
                if (screen != null) {
                    try {
                        Field w = Screen.class.getDeclaredField("width");
                        w.setAccessible(true); w.set(screen, 400);
                        Field h = Screen.class.getDeclaredField("height");
                        h.setAccessible(true); h.set(screen, 300);
                        Method initMethod = Screen.class.getDeclaredMethod("init");
                        initMethod.setAccessible(true);
                        initMethod.invoke(screen);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }).when(mcMock).setScreen(Mockito.any());
        } else {
            mcMock.screen = null; // reset for next test
        }
        
        if (mockedFabricLoader == null) {
            mockedFabricLoader = Mockito.mockStatic(FabricLoader.class);
            loaderMock = Mockito.mock(FabricLoader.class);
            mockedFabricLoader.when(FabricLoader::getInstance).thenReturn(loaderMock);
            Mockito.when(loaderMock.getConfigDir()).thenReturn(Paths.get("config"));
        }
        if (mockedKeyMappingHelper == null) {
            mockedKeyMappingHelper = Mockito.mockStatic(KeyMappingHelper.class);
            mockedKeyMappingHelper.when(() -> KeyMappingHelper.registerKeyMapping(Mockito.any())).thenAnswer(i -> i.getArgument(0));
        }
    }

    public static synchronized void teardownMocks() {
        if (mockedMinecraft != null) {
            mockedMinecraft.close();
            mockedMinecraft = null;
        }
        if (mockedFabricLoader != null) {
            mockedFabricLoader.close();
            mockedFabricLoader = null;
        }
        if (mockedKeyMappingHelper != null) {
            mockedKeyMappingHelper.close();
            mockedKeyMappingHelper = null;
        }
    }

    public static Object getConfig() throws Exception {
        Class<?> configManagerClass = Class.forName("com.thelads.client.config.ConfigManager");
        Method getConfigMethod = configManagerClass.getDeclaredMethod("getConfig");
        getConfigMethod.setAccessible(true);
        return getConfigMethod.invoke(null);
    }

    public static boolean isCapesEnabled() throws Exception {
        Object config = getConfig();
        Method isCapesEnabledMethod = config.getClass().getDeclaredMethod("isCapesEnabled");
        isCapesEnabledMethod.setAccessible(true);
        return (boolean) isCapesEnabledMethod.invoke(config);
    }

    public static boolean isUiScalingEnabled() throws Exception {
        Object config = getConfig();
        Method isUiScalingEnabledMethod = config.getClass().getDeclaredMethod("isUiScalingEnabled");
        isUiScalingEnabledMethod.setAccessible(true);
        return (boolean) isUiScalingEnabledMethod.invoke(config);
    }
    
    public static void setCapesEnabled(boolean enabled) throws Exception {
        Object config = getConfig();
        Method setCapesEnabledMethod = config.getClass().getDeclaredMethod("setCapesEnabled", boolean.class);
        setCapesEnabledMethod.setAccessible(true);
        setCapesEnabledMethod.invoke(config, enabled);
    }

    public static void setUiScalingEnabled(boolean enabled) throws Exception {
        Object config = getConfig();
        Method setUiScalingEnabledMethod = config.getClass().getDeclaredMethod("setUiScalingEnabled", boolean.class);
        setUiScalingEnabledMethod.setAccessible(true);
        setUiScalingEnabledMethod.invoke(config, enabled);
    }

    public static void save() throws Exception {
        Class<?> configManagerClass = Class.forName("com.thelads.client.config.ConfigManager");
        Method saveConfigMethod = configManagerClass.getDeclaredMethod("save");
        saveConfigMethod.setAccessible(true);
        saveConfigMethod.invoke(null);
    }

    public static void loadConfig() throws Exception {
        Class<?> configManagerClass = Class.forName("com.thelads.client.config.ConfigManager");
        Method loadConfigMethod = configManagerClass.getDeclaredMethod("loadConfig");
        loadConfigMethod.setAccessible(true);
        loadConfigMethod.invoke(null);
    }

    public static void simulateRightShiftKeyPress() throws Exception {
        try {
            // Initialize mod if not initialized
            Class<?> modClassMain = Class.forName("com.thelads.client.TheLadsClientMod");
            java.lang.reflect.Field kbFieldMain = modClassMain.getDeclaredField("rightShiftKeyBinding");
            kbFieldMain.setAccessible(true);
            if (kbFieldMain.get(null) == null) {
                modClassMain.getDeclaredMethod("onInitializeClient").invoke(modClassMain.getDeclaredConstructor().newInstance());
            }

            // Also explicitly press our mod's keybinding since the static map might not be updated in test env
            Class<?> inputConstantsTypeClass = Class.forName("com.mojang.blaze3d.platform.InputConstants$Type");
            java.lang.reflect.Field keysymField = inputConstantsTypeClass.getField("KEYSYM");
            Object keysym = keysymField.get(null);
            Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            int rightShift = glfwClass.getField("GLFW_KEY_RIGHT_SHIFT").getInt(null);
            Method getOrCreate = keysym.getClass().getMethod("getOrCreate", int.class);
            Object key = getOrCreate.invoke(keysym, rightShift);
            Class<?> inputConstantsKeyClass = Class.forName("com.mojang.blaze3d.platform.InputConstants$Key");
            Class<?> keyMappingClass = Class.forName("net.minecraft.client.KeyMapping");
            Method clickMethod = keyMappingClass.getMethod("click", inputConstantsKeyClass);
            clickMethod.invoke(null, key);

            // Simulate tick event so TheLadsClientMod tick listener fires
            Class<?> clientTickEventsClass = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents");
            java.lang.reflect.Field endClientTickField = clientTickEventsClass.getDeclaredField("END_CLIENT_TICK");
            endClientTickField.setAccessible(true);
            Object event = endClientTickField.get(null);
            Method invokerMethod = event.getClass().getMethod("invoker");
            Object invoker = invokerMethod.invoke(event);
            Class<?> clientTickEventClass = Class.forName("net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents$EndTick");
            Method onEndTickMethod = clientTickEventClass.getMethod("onEndTick", Class.forName("net.minecraft.client.Minecraft"));
            
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Method getInstance = mcClass.getDeclaredMethod("getInstance");
            Object mc = getInstance.invoke(null);
            onEndTickMethod.invoke(invoker, mc);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Error inside simulateRightShiftKeyPress: " + t.toString(), t);
        }
    }

    public static Object getCurrentScreen() throws Exception {
        return Minecraft.getInstance().screen;
    }

    public static boolean hasWidgetWithText(Object screen, String text) throws Exception {
        if (screen == null) return false;
        // Search through renderables/children
        Method getChildren = screen.getClass().getSuperclass().getDeclaredMethod("children");
        getChildren.setAccessible(true);
        java.util.List<?> children = (java.util.List<?>) getChildren.invoke(screen);
        for (Object child : children) {
            Method getMessage;
            try {
                getMessage = child.getClass().getMethod("getMessage");
            } catch (NoSuchMethodException e) {
                continue;
            }
            getMessage.setAccessible(true);
            Object textObj = getMessage.invoke(child);
            Method getString = textObj.getClass().getMethod("getString");
            getString.setAccessible(true);
            String message = (String) getString.invoke(textObj);
            if (message != null && message.contains(text)) {
                return true;
            }
        }
        return false;
    }
}
