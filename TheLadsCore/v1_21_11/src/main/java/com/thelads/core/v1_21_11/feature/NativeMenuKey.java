package com.thelads.core.v1_21_11.feature;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.MenuKeyController;
import com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

/** Only gameplay and the Lads menu participate; chat, Controls and other screens keep their input. */
public final class NativeMenuKey {
    private static final MenuKeyController CONTROLLER = new MenuKeyController();
    private static Object player;
    private NativeMenuKey() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        // A focus/world change can lose the release callback. The next real press must work.
        if (!mc.isWindowActive() || player != mc.player) CONTROLLER.reset();
        player = mc.player;
    }

    private static MenuKeyController.Action action(int action) {
        return switch (action) {
            case GLFW.GLFW_PRESS -> MenuKeyController.Action.PRESS;
            case GLFW.GLFW_RELEASE -> MenuKeyController.Action.RELEASE;
            default -> MenuKeyController.Action.REPEAT;
        };
    }

    public static boolean key(KeyEvent event, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isWindowActive()) { CONTROLLER.reset(); return false; }
        Screen screen = mc.screen;
        var decision = CONTROLLER.key(event.key(), event.scancode(), action(action),
            NativeKeyBindings.MODULES.matches(event), NativeFeatures.interactive(),
            screen instanceof LadsSettingsScreen12111);
        if (decision == MenuKeyController.Decision.PASS) return false;
        if (decision == MenuKeyController.Decision.OPEN) {
            NativeFeatures.reset();
            player = mc.player;
            mc.setScreen(new LadsSettingsScreen12111(null));
        } else if (decision == MenuKeyController.Decision.TRY_CLOSE) {
            // Common editing/navigation consumes its keys first, including Shift while typing.
            LadsSettingsScreen12111 menu = (LadsSettingsScreen12111) screen;
            if (!menu.keyPressed(event)) menu.closeFromMenuKey();
            menu.afterKeyboardAction();
            if (mc.screen == screen) return true;
            CONTROLLER.capture(event.key(), event.scancode());
            NativeFeatures.reset();
        }
        // Grabbing the mouse on close polls physically held keys. Do not let the consumed
        // menu key become crouch/zoom/another gameplay action. Vanilla toggle preferences stay native.
        KeyMapping.set(InputConstants.getKey(event), false);
        return true;
    }

    public static boolean mouse(MouseButtonEvent event, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isWindowActive()) { CONTROLLER.reset(); return false; }
        Screen screen = mc.screen;
        int physicalKey = -1000 - event.button(); // Separate mouse and keyboard/scancode identities.
        var decision = CONTROLLER.key(physicalKey, 0, action(action),
            NativeKeyBindings.MODULES.matchesMouse(event), NativeFeatures.interactive(),
            screen instanceof LadsSettingsScreen12111);
        if (decision == MenuKeyController.Decision.PASS) return false;
        if (decision == MenuKeyController.Decision.OPEN) {
            NativeFeatures.reset();
            player = mc.player;
            mc.setScreen(new LadsSettingsScreen12111(null));
        } else if (decision == MenuKeyController.Decision.TRY_CLOSE) {
            LadsSettingsScreen12111 menu = (LadsSettingsScreen12111) screen;
            if (!menu.mouseClicked(event, false)) menu.closeFromMenuKey();
            menu.afterMouseAction();
            if (mc.screen == screen) return true;
            CONTROLLER.capture(physicalKey, 0);
            NativeFeatures.reset();
        }
        KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(event.button()), false);
        return true;
    }
}
