package com.thelads.core.v1_20_1.hud;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Forge (Zero SMP, through Sinytra Connector) draws the HUD with its own ForgeGui, which replaces Gui.render, so the call sites
 * HudParts1201 wraps on plain Fabric are never reached. There each vanilla overlay of the hotbar group is bracketed through
 * Forge's RenderGuiOverlayEvent.Pre/Post, reached by reflection so the Core needs nothing of Forge at compile time.
 */
public final class ForgeOverlays1201 {
    private static final Set<String> GROUP = Set.of("hotbar", "player_health", "armor_level", "food_level", "air_level", "mount_health",
        "jump_bar", "experience_bar", "item_name");
    private static final Deque<Object[]> OPEN = new ArrayDeque<>();
    private static boolean registered;
    private static Method guiGraphics, overlay, id, cancelled, cancel;

    private ForgeOverlays1201() {}

    /** True when this game runs on Forge. */
    public static boolean present() {
        try {
            Class.forName("net.minecraftforge.client.event.RenderGuiOverlayEvent$Pre");
            return true;
        } catch (ClassNotFoundException | LinkageError error) {
            return false;
        }
    }

    public static void register() {
        if (registered || !present()) return;
        registered = true;
        try {
            Class<?> base = Class.forName("net.minecraftforge.client.event.RenderGuiOverlayEvent");
            Class<?> pre = Class.forName("net.minecraftforge.client.event.RenderGuiOverlayEvent$Pre");
            Class<?> post = Class.forName("net.minecraftforge.client.event.RenderGuiOverlayEvent$Post");
            Class<?> event = Class.forName("net.minecraftforge.eventbus.api.Event");
            guiGraphics = base.getMethod("getGuiGraphics");
            overlay = base.getMethod("getOverlay");
            id = overlay.getReturnType().getMethod("id");
            cancelled = event.getMethod("isCanceled");
            cancel = event.getMethod("setCanceled", boolean.class);
            Object bus = Class.forName("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
            Class<?> priority = Class.forName("net.minecraftforge.eventbus.api.EventPriority");
            Method add = Class.forName("net.minecraftforge.eventbus.api.IEventBus")
                .getMethod("addListener", priority, boolean.class, Class.class, Consumer.class);
            Object highest = priority.getField("HIGHEST").get(null), lowest = priority.getField("LOWEST").get(null);
            Consumer<Object> onPre = ForgeOverlays1201::pre, onPreLast = ForgeOverlays1201::preLast, onPost = ForgeOverlays1201::post;
            add.invoke(bus, highest, true, pre, onPre);
            add.invoke(bus, lowest, true, pre, onPreLast);
            add.invoke(bus, lowest, true, post, onPost);
            Hud1201.LOG.info("Lads HUD parts: hooked Forge's overlay events");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Hud1201.LOG.warn("Lads HUD parts: Forge's overlay events are not available, Raised and Auto Hide only act on the Lads HUD", failure);
        }
        try {
            // ForgeGui draws the debug text itself (not DebugScreenOverlay.renderLines), from this event's lists once every listener has run.
            Class<?> text = Class.forName("net.minecraftforge.client.event.CustomizeGuiOverlayEvent$DebugText");
            Method left = text.getMethod("getLeft"), right = text.getMethod("getRight");
            Object bus = Class.forName("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
            Class<?> priority = Class.forName("net.minecraftforge.eventbus.api.EventPriority");
            Consumer<Object> onText = event -> debugText(event, left, right);
            Class.forName("net.minecraftforge.eventbus.api.IEventBus").getMethod("addListener", priority, boolean.class, Class.class, Consumer.class)
                .invoke(bus, priority.getField("LOWEST").get(null), true, text, onText);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Hud1201.LOG.warn("Lads HUD parts: Forge's debug text event is not available, F3 does not cover the Lads HUD", failure);
        }
    }

    /** HudCover: the debug text ForgeGui is about to draw covers the Lads HUD widgets under its lines. */
    @SuppressWarnings("unchecked")
    private static void debugText(Object event, Method left, Method right) {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            int width = mc.getWindow().getGuiScaledWidth();
            com.thelads.core.client.hud.HudCover.debugColumn((java.util.List<String>) left.invoke(event), true, width, mc.font::width);
            com.thelads.core.client.hud.HudCover.debugColumn((java.util.List<String>) right.invoke(event), false, width, mc.font::width);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Hud1201.LOG.warn("Lads HUD parts: Forge's debug text could not be read", failure);
        }
    }

    private static String path(Object event) throws ReflectiveOperationException {
        Object name = id.invoke(overlay.invoke(event));
        return name == null ? "" : name.toString().substring(name.toString().indexOf(':') + 1);
    }

    /**
     * Ends every part still open when {@code current} (the next overlay's Pre, or null at the end of the GUI) begins: overlays run one
     * after the other, so a part left open belongs to an overlay that another mod cancelled after our last look (its Post never
     * comes). Left open, its Raised lift stayed on the pose for everything drawn after it, which on Zero SMP shifted every screen of
     * the frame (Lads settings, HUD editor, pause, inventory) up by the lift.
     */
    static void closeUnfinished(Object current) {
        Object[] top;
        while ((top = OPEN.peek()) != null && top[0] != current) {
            OPEN.pop();
            HudParts1201.end((GuiGraphics) top[1], (HudParts1201.Scope) top[2]);
        }
    }

    private static void pre(Object event) {
        try {
            closeUnfinished(event);
            if (Boolean.TRUE.equals(cancelled.invoke(event))) return; // someone else already skipped it: no Post will come
            String name = path(event);
            // Xaero's minimap (its overlay xaerominimap:xaero) steps aside with the Lads Minimap widget under the tab list or F3 (HudCover).
            if ("xaero".equals(name) && com.thelads.core.client.hud.HudManager.getInstance().isCovered("Minimap")) {
                cancel.invoke(event, true);
                return;
            }
            boolean group = GROUP.contains(name), record = name.contains("record");
            if (!group && !record) return;
            GuiGraphics graphics = (GuiGraphics) guiGraphics.invoke(event);
            HudParts1201.Scope scope = HudParts1201.begin(graphics, group);
            if (scope == null) {
                cancel.invoke(event, true); // fully hidden: Forge skips the overlay, and its Post does not come
                return;
            }
            OPEN.push(new Object[] {event, graphics, scope});
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Hud1201.LOG.warn("Lads HUD parts: a Forge overlay could not be wrapped", failure);
        }
    }

    /** Another listener cancelled the overlay after we began its part: its Post never comes, so end the part now. */
    private static void preLast(Object event) {
        try {
            Object[] top = OPEN.peek();
            if (top != null && top[0] == event && Boolean.TRUE.equals(cancelled.invoke(event))) {
                OPEN.pop();
                HudParts1201.end((GuiGraphics) top[1], (HudParts1201.Scope) top[2]);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Hud1201.LOG.warn("Lads HUD parts: a cancelled Forge overlay could not be closed", failure);
        }
    }

    private static void post(Object event) {
        try {
            Object[] top = OPEN.peek();
            if (top == null) return;
            GuiGraphics graphics = (GuiGraphics) guiGraphics.invoke(event);
            if (top[1] != graphics) return;
            OPEN.pop();
            HudParts1201.end(graphics, (HudParts1201.Scope) top[2]);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Hud1201.LOG.warn("Lads HUD parts: a Forge overlay could not be closed", failure);
        }
    }
}
