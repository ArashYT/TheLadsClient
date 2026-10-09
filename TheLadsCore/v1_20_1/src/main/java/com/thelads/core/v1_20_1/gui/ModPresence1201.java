package com.thelads.core.v1_20_1.gui;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.LoggerFactory;

/**
 * Which mods are installed, on plain Fabric and inside Zero SMP (Forge + Sinytra Connector): Fabric mods answer to
 * FabricLoader, Forge mods to Forge's ModList (Connector only bridges some of them into FabricLoader).
 */
public final class ModPresence1201 {
    private static final boolean FORGE = classExists("net.minecraftforge.fml.ModList");
    private static final Method FORGE_LOADED = FORGE ? method("net.minecraftforge.fml.ModList", "isLoaded", String.class) : null;

    private ModPresence1201() {}

    public static boolean forge() {
        return FORGE;
    }

    public static boolean isLoaded(String id) {
        if (id == null || id.isBlank()) return false;
        if (FabricLoader.getInstance().isModLoaded(id)) return true;
        if (!FORGE) return false;
        try {
            Object list = Class.forName("net.minecraftforge.fml.ModList").getMethod("get").invoke(null);
            return list != null && Boolean.TRUE.equals(FORGE_LOADED.invoke(list, id));
        } catch (ReflectiveOperationException | LinkageError failure) {
            return false;
        }
    }

    /** A Forge mod's registered config screen (its ConfigScreenFactory extension), or null. */
    public static Screen forgeConfigScreen(String id, Screen parent) {
        if (!FORGE) return null;
        try {
            Class<?> modList = Class.forName("net.minecraftforge.fml.ModList");
            Object list = modList.getMethod("get").invoke(null);
            Optional<?> container = (Optional<?>) modList.getMethod("getModContainerById", String.class).invoke(list, id);
            if (container == null || container.isEmpty()) return null;
            Class<?> factoryType = Class.forName("net.minecraftforge.client.ConfigScreenHandler$ConfigScreenFactory");
            Optional<?> factory = (Optional<?>) container.get().getClass().getMethod("getCustomExtension", Class.class).invoke(container.get(), factoryType);
            if (factory == null || factory.isEmpty()) return null;
            @SuppressWarnings("unchecked")
            BiFunction<Minecraft, Screen, Screen> function = (BiFunction<Minecraft, Screen, Screen>) factoryType.getMethod("screenFunction").invoke(factory.get());
            return function.apply(Minecraft.getInstance(), parent);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            LoggerFactory.getLogger("TheLadsCore").debug("No Forge config screen for {}", id, failure);
            return null;
        }
    }

    /** Forge's own mod list ("Mods" on the title screen), or null on plain Fabric. */
    public static Screen forgeModList(Screen parent) {
        if (!FORGE) return null;
        try {
            return (Screen) Class.forName("net.minecraftforge.client.gui.ModListScreen").getConstructor(Screen.class).newInstance(parent);
        } catch (ReflectiveOperationException | LinkageError failure) {
            return null;
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, ModPresence1201.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    private static Method method(String type, String name, Class<?>... parameters) {
        try {
            return Class.forName(type).getMethod(name, parameters);
        } catch (ReflectiveOperationException | LinkageError missing) {
            return null;
        }
    }
}
