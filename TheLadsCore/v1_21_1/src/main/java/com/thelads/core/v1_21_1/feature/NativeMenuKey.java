package com.thelads.core.v1_21_1.feature;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.MenuKeyController;
import com.thelads.core.v1_21_1.gui.LadsSettingsScreen121;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

/** Gameplay, title and pause menus participate; text fields and inventories keep their input. 26.x routing, 1.21.1 raw input. */
public final class NativeMenuKey {
    private static final MenuKeyController CONTROLLER = new MenuKeyController();
    private static Object player;
    private NativeMenuKey() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        // A focus/world change can lose the release callback. The next real press must work.
        if (!NativeWorldVerification.windowActive() || player != mc.player) CONTROLLER.reset();
        player = mc.player;
    }

    private static MenuKeyController.Action action(int action) {
        return switch (action) {
            case GLFW.GLFW_PRESS -> MenuKeyController.Action.PRESS;
            case GLFW.GLFW_RELEASE -> MenuKeyController.Action.RELEASE;
            default -> MenuKeyController.Action.REPEAT;
        };
    }

    private static boolean canOpen(Screen screen) {
        return NativeFeatures.interactive() || screen instanceof TitleScreen || screen instanceof PauseScreen;
    }

    public static boolean key(int key, int scancode, int action, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        if (!NativeWorldVerification.windowActive()) { CONTROLLER.reset(); return false; }
        Screen screen = mc.screen;
        var decision = CONTROLLER.key(key, scancode, action(action),
            NativeKeyBindings.MODULES.matches(key, scancode), canOpen(screen),
            screen instanceof LadsSettingsScreen121);
        if (decision == MenuKeyController.Decision.PASS) return false;
        if (decision == MenuKeyController.Decision.OPEN) {
            NativeFeatures.reset();
            player = mc.player;
            mc.setScreen(new LadsSettingsScreen121(screen));
        } else if (decision == MenuKeyController.Decision.TRY_CLOSE) {
            // Common editing/navigation consumes its keys first, including Shift while typing.
            LadsSettingsScreen121 menu = (LadsSettingsScreen121) screen;
            if (!menu.keyPressed(key, scancode, modifiers)) menu.closeFromMenuKey();
            menu.afterKeyboardAction();
            if (mc.screen == screen) return true;
            CONTROLLER.capture(key, scancode);
            NativeFeatures.reset();
        }
        // Grabbing the mouse on close polls physically held keys. Do not let the consumed
        // menu key become crouch/zoom/another gameplay action. Vanilla toggle preferences stay native.
        KeyMapping.set(InputConstants.getKey(key, scancode), false);
        return true;
    }

    public static boolean mouse(int button, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (!NativeWorldVerification.windowActive()) { CONTROLLER.reset(); return false; }
        Screen screen = mc.screen;
        int physicalKey = -1000 - button; // Separate mouse and keyboard/scancode identities.
        var decision = CONTROLLER.key(physicalKey, 0, action(action),
            NativeKeyBindings.MODULES.matchesMouse(button), canOpen(screen),
            screen instanceof LadsSettingsScreen121);
        if (decision == MenuKeyController.Decision.PASS) return false;
        if (decision == MenuKeyController.Decision.OPEN) {
            NativeFeatures.reset();
            player = mc.player;
            mc.setScreen(new LadsSettingsScreen121(screen));
        } else if (decision == MenuKeyController.Decision.TRY_CLOSE) {
            LadsSettingsScreen121 menu = (LadsSettingsScreen121) screen;
            // Same GUI-scaled position MouseHandler.onPress gives screens on 1.21.1.
            var window = mc.getWindow();
            double x = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
            double y = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
            if (!menu.mouseClicked(x, y, button)) menu.closeFromMenuKey();
            menu.afterMouseAction();
            if (mc.screen == screen) return true;
            CONTROLLER.capture(physicalKey, 0);
            NativeFeatures.reset();
        }
        KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(button), false);
        return true;
    }
}
