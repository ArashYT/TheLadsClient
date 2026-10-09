package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.title.TitleScreenTheme;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Only replaces drawing during the owning title screen's native widget pass. */
public final class TitleWidgetRegistry1201 {
    private static final Map<AbstractWidget, Entry> WIDGETS = new WeakHashMap<>();
    private static Screen frameOwner;
    private static LadsGraphics frameGraphics;
    private static float frameDelta;
    private static boolean reducedMotion;

    private TitleWidgetRegistry1201() {}

    public static void register(Screen owner, AbstractWidget widget, String icon, boolean primary) {
        WIDGETS.put(widget, new Entry(owner, icon, primary, renderLabel(widget), widget.getMessage()));
    }

    /** The translation key of a widget's message, or "" for literal text. */
    public static String messageKey(AbstractWidget widget) {
        Component message = widget.getMessage();
        return message != null && message.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    /** The short English label drawn for a title widget (More draws its copies with it too). */
    public static String renderLabel(AbstractWidget widget) {
        Component message = widget.getMessage();
        if (message == null) return "";
        String original = message.getString();
        // 1.20.1's copyright line is a PlainTextButton (a literal "Copyright Mojang AB. Do not distribute!").
        if (widget instanceof PlainTextButton) return "Credits";
        if (!(message.getContents() instanceof TranslatableContents contents)) return original;
        String key = contents.getKey();
        if ("menu.online".equals(key)) return "Realms";
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (language == null || !language.startsWith("en_")) return original;
        // Drawing only: the native component still owns narration, tooltips and actions.
        return switch (key) {
            case "menu.options" -> "Options";
            case "options.language", "narrator.button.language" -> "Language";
            case "options.accessibility", "narrator.button.accessibility", "accessibility.onboarding.accessibility.button" -> "Accessibility";
            case "title.credits" -> "Credits";
            case "modmenu.title" -> "Mods"; // without Mod Menu's mod count, which would not fit
            case "fml.menu.mods" -> "Mods";
            case "screen.lads_screenshots.manage_screenshots" -> "Screenshots";
            default -> original;
        };
    }

    /**
     * A label for an icon-only button another mod added (Create's, Quark's, Supplementaries' config buttons have no text): the mod
     * it came from, read off the jar its class was loaded from, e.g. "Create settings".
     */
    public static String guessLabel(AbstractWidget widget) {
        try {
            var source = widget.getClass().getProtectionDomain().getCodeSource();
            if (source != null) {
                String location = java.net.URLDecoder.decode(source.getLocation().toString(), java.nio.charset.StandardCharsets.UTF_8);
                var matcher = java.util.regex.Pattern.compile("([^/\\\\!#]+?)\\.jar").matcher(location);
                String jar = null;
                while (matcher.find()) jar = matcher.group(1);
                if (jar != null) {
                    String name = jar.replaceFirst("(?i)[-_ +]*(forge|fabric|neoforge|mc)?[-_ +]*v?\\d.*$", "").replaceAll("[-_]+", " ").trim();
                    if (!name.isEmpty()) return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " settings";
                }
            }
        } catch (RuntimeException ignored) {}
        return "Extra settings";
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
        Runnable draw = () -> TitleScreenTheme.renderButton(frameGraphics, widget.getX(), widget.getY(), widget.getWidth(),
            widget.getHeight(), entry.label,
            entry.icon, entry.primary, widget.isHovered(), widget.isFocused(), widget.active, entry.hover);
        if (frameGraphics instanceof com.thelads.core.v1_20_1.adapter.GuiGraphicsLadsAdapter1201 adapter) adapter.managed(draw);
        else draw.run();
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
