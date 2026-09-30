package com.thelads.core.v1_21_1.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.title.TitleScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Only replaces drawing during the owning title screen's native widget pass. */
public final class TitleWidgetRegistry {
    private static final Map<AbstractWidget, Entry> WIDGETS = new WeakHashMap<>();
    private static Screen frameOwner;
    private static LadsGraphics frameGraphics;
    private static float frameDelta;
    private static boolean reducedMotion;

    private TitleWidgetRegistry() {}

    public static void register(Screen owner, AbstractWidget widget, String icon, boolean primary) {
        WIDGETS.put(widget, new Entry(owner, icon, primary, renderLabel(widget), widget.getMessage()));
    }

    private static String renderLabel(AbstractWidget widget) {
        Component message = widget.getMessage();
        if (message == null) return "";
        String original = message.getString();
        if (!(message.getContents() instanceof TranslatableContents contents)) return original;
        String key = contents.getKey();
        if ("menu.online".equals(key)) return "Realms";
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (language == null || !language.startsWith("en_")) return original;
        // Drawing only: the native component still owns narration, tooltips and actions.
        return switch (key) {
            case "menu.options" -> "Options";
            case "options.language" -> "Language";
            case "options.accessibility", "accessibility.onboarding.accessibility.button" -> "Accessibility";
            case "title.credits" -> "Credits";
            default -> original;
        };
    }

    public static void unregister(AbstractWidget widget) {
        WIDGETS.remove(widget);
    }

    public static void beginFrame(Screen owner, LadsGraphics graphics, float delta, boolean reduceMotion) {
        frameOwner = owner;
        frameGraphics = graphics;
        frameDelta = delta;
        reducedMotion = reduceMotion;
    }

    public static void endFrame() {
        frameOwner = null;
        frameGraphics = null;
    }

    public static boolean render(AbstractWidget widget) {
        if (frameGraphics == null) return false;
        Entry entry = WIDGETS.get(widget);
        if (entry == null || entry.owner.get() != frameOwner) return false;
        if (!java.util.Objects.equals(entry.message, widget.getMessage())) {
            entry.label = renderLabel(widget);
            entry.message = widget.getMessage() == null ? null : widget.getMessage().copy();
        }
        float target = widget.active && widget.isHoveredOrFocused() ? 1 : 0;
        float step = frameDelta * 8;
        entry.hover = reducedMotion ? target : entry.hover < target
            ? Math.min(target, entry.hover + step) : Math.max(target, entry.hover - step);
        TitleScreenTheme.renderButton(frameGraphics, widget.getX(), widget.getY(), widget.getWidth(),
            widget.getHeight(), entry.label,
            entry.icon, entry.primary, widget.isHovered(), widget.isFocused(), widget.active, entry.hover);
        return true;
    }

    private static final class Entry {
        private final WeakReference<Screen> owner;
        private final String icon;
        private final boolean primary;
        private String label;
        private Component message;
        private float hover;

        private Entry(Screen owner, String icon, boolean primary, String label, Component message) {
            this.owner = new WeakReference<>(owner);
            this.icon = icon;
            this.primary = primary;
            this.label = label;
            this.message = message == null ? null : message.copy();
        }
    }
}
