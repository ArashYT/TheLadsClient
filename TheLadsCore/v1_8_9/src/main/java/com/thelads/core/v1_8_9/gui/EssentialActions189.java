package com.thelads.core.v1_8_9.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;

/** Essential's title and pause buttons on 1.8.9: the same proxy GuiButtons and Elementa layer as on the other versions (EssentialActions). */
public final class EssentialActions189 {
    private static boolean warned;
    private EssentialActions189() {}

    public static boolean isEssential(GuiButton button) {
        return button.getClass().getName().startsWith("gg.essential.");
    }

    /** Essential's menu is its own Elementa layer, added again after every init of the screen: removed on every draw. */
    public static void suppressOverlay(GuiScreen screen) {
        if (!Loader.isModLoaded("essential")) return;
        try {
            Object handler = screen.getClass().getMethod("essential$getProxyHandler").invoke(screen);
            if (handler == null) return;
            Object layer = handler.getClass().getMethod("getLayer").invoke(handler);
            if (layer != null) {
                Class<?> util = Class.forName("gg.essential.util.GuiUtil");
                util.getMethod("removeLayer", Class.forName("gg.essential.gui.overlay.Layer")).invoke(util.getField("INSTANCE").get(null), layer);
            }
        } catch (NoSuchMethodException ignored) {
        } catch (ReflectiveOperationException failure) {
            if (!warned) {
                warned = true;
                LogManager.getLogger("TheLadsCore").warn("Essential menu layer integration is unavailable", failure);
            }
        }
    }

    /** The proxy's real Essential action (Social, Wardrobe, Pictures...), or null for badges, notices and unknown proxies. */
    public static TitleExtrasScreen189.Action capture(GuiButton proxy) {
        if (!isEssential(proxy)) return null;
        try {
            Class<?> type = proxy.getClass();
            Object component = type.getMethod("getEssentialComponent").invoke(proxy);
            String id = String.valueOf(type.getMethod("getEssentialId").invoke(proxy));
            String key = id.toLowerCase(Locale.ROOT);
            if (component == null) {
                Object container = field(proxy, "essentialContainer");
                if (container != null) component = findButton(container);
            }
            if (component == null || key.contains("badge") || key.contains("notification") || key.contains("player") || key.contains("new")
                || key.contains("count")) return null;
            Method action = null;
            for (Class<?> c = type; c != null && action == null; c = c.getSuperclass())
                for (Method m : c.getDeclaredMethods())
                    if (m.getName().equals("click") && m.getParameterCount() == 1 && !m.isBridge() && m.getParameterTypes()[0].isInstance(component)) {
                        action = m;
                        break;
                    }
            if (action == null) return null;
            action.setAccessible(true);
            Method click = action;
            Object target = component;
            String label = key.contains("social") || key.contains("friend") ? "Social" : key.contains("wardrobe") || key.contains("cosmetic") ? "Wardrobe"
                : key.contains("picture") || key.contains("screenshot") ? "Pictures" : key.contains("host") || key.contains("invite") ? "Host world"
                : key.contains("setting") ? "Essential settings" : id.replaceAll("(?i)essential[._:-]?", "").replace('_', ' ').replace('-', ' ');
            return new TitleExtrasScreen189.Action(label, () -> {
                try {
                    click.invoke(proxy, target);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Could not open Essential action " + id, failure);
                }
            }, proxy.enabled);
        } catch (ReflectiveOperationException failure) {
            LogManager.getLogger("TheLadsCore").warn("Essential menu integration unavailable for {}", proxy.getClass().getName(), failure);
            return null;
        }
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass())
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(object);
            } catch (NoSuchFieldException ignored) {}
        return null;
    }

    private static Object findButton(Object component) throws ReflectiveOperationException {
        if (Class.forName("gg.essential.gui.common.MenuButton").isInstance(component)) return component;
        for (Object child : (Iterable<?>) component.getClass().getMethod("getChildren").invoke(component)) {
            Object button = findButton(child);
            if (button != null) return button;
        }
        return null;
    }
}
