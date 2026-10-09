package com.thelads.core.v1_20_1.feature;

import com.thelads.core.client.MenuKeyController;
import com.thelads.core.v1_20_1.gui.LadsSettingsScreen1201;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class NativeMenuKey1201 {
    private static final MenuKeyController CONTROLLER = new MenuKeyController();

    private NativeMenuKey1201() {}

    private static MenuKeyController.Action action(int action) {
        return switch (action) {
            case GLFW.GLFW_PRESS -> MenuKeyController.Action.PRESS;
            case GLFW.GLFW_RELEASE -> MenuKeyController.Action.RELEASE;
            default -> MenuKeyController.Action.REPEAT;
        };
    }

    private static boolean canOpen(Screen screen) {
        return NativeFeatures1201.interactive() || screen instanceof TitleScreen || screen instanceof PauseScreen;
    }

    public static boolean key(int key, int scancode, int action, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isWindowActive()) {
            CONTROLLER.reset();
            return false;
        }
        Screen screen = mc.screen;
        boolean matches = NativeKeyBindings1201.MODULES.matches(key, scancode);
        var decision = CONTROLLER.key(key, scancode, action(action),
            matches, canOpen(screen),
            screen instanceof LadsSettingsScreen1201);

        if (decision == MenuKeyController.Decision.PASS) return false;
        if (decision == MenuKeyController.Decision.OPEN) {
            NativeFeatures1201.reset();
            mc.setScreen(new LadsSettingsScreen1201(screen));
        } else if (decision == MenuKeyController.Decision.TRY_CLOSE) {
            if (screen instanceof LadsSettingsScreen1201 menu) {
                menu.closeFromMenuKey();
                if (mc.screen == screen) return true;
                CONTROLLER.capture(key, scancode);
                NativeFeatures1201.reset();
            }
        }
        KeyMapping.set(InputConstants.getKey(key, scancode), false);
        return true;
    }

    public static boolean mouse(int button, int action, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isWindowActive()) {
            CONTROLLER.reset();
            return false;
        }
        Screen screen = mc.screen;
        int physicalKey = -1000 - button;
        boolean matches = NativeKeyBindings1201.MODULES.matchesMouse(button);
        var decision = CONTROLLER.key(physicalKey, 0, action(action),
            matches, canOpen(screen),
            screen instanceof LadsSettingsScreen1201);

        if (decision == MenuKeyController.Decision.PASS) return false;
        if (decision == MenuKeyController.Decision.OPEN) {
            NativeFeatures1201.reset();
            mc.setScreen(new LadsSettingsScreen1201(screen));
        } else if (decision == MenuKeyController.Decision.TRY_CLOSE) {
            if (screen instanceof LadsSettingsScreen1201 menu) {
                menu.closeFromMenuKey();
                if (mc.screen == screen) return true;
                CONTROLLER.capture(physicalKey, 0);
                NativeFeatures1201.reset();
            }
        }
        KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(button), false);
        return true;
    }
}
