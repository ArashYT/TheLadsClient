package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Gameplay, title and pause menus participate; text fields and inventories keep their input. */
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
            case InputConstants.PRESS -> MenuKeyController.Action.PRESS;
            case InputConstants.RELEASE -> MenuKeyController.Action.RELEASE;
            default -> MenuKeyController.Action.REPEAT;
        };
    }

    private static boolean canOpen(Screen screen) {
        return NativeFeatures.interactive() || screen instanceof TitleScreen || screen instanceof PauseScreen;
    }

    public static boolean key(KeyEvent event, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isWindowActive()) { CONTROLLER.reset(); return false; }
        Screen screen = mc.gui.screen();
        var decision = CONTROLLER.key(event.key(), event.keycode(), action(action),
            NativeKeyBindings.MODULES.matches(event), canOpen(screen),
            screen instanceof LadsSettingsScreen26);
        if (decision == MenuKeyController.Decision.PASS) return false;
        if (decision == MenuKeyController.Decision.OPEN) {
            NativeFeatures.reset();
            player = mc.player;
            mc.gui.setScreen(new LadsSettingsScreen26(screen));
        } else if (decision == MenuKeyController.Decision.TRY_CLOSE) {
            // Common editing/navigation consumes its keys first, including Shift while typing.
            LadsSettingsScreen26 menu = (LadsSettingsScreen26) screen;
            if (!menu.keyPressed(event)) menu.closeFromMenuKey();
            menu.afterKeyboardAction();
            if (mc.gui.screen() == screen) return true;
            CONTROLLER.capture(event.key(), event.keycode());
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
        Screen screen = mc.gui.screen();
        int physicalKey = -1000 - event.button(); // Separate mouse and keyboard/scancode identities.
        var decision = CONTROLLER.key(physicalKey, 0, action(action),
            NativeKeyBindings.MODULES.matchesMouse(event), canOpen(screen),
            screen instanceof LadsSettingsScreen26);
        if (decision == MenuKeyController.Decision.PASS) return false;
        if (decision == MenuKeyController.Decision.OPEN) {
            NativeFeatures.reset();
            player = mc.player;
            mc.gui.setScreen(new LadsSettingsScreen26(screen));
        } else if (decision == MenuKeyController.Decision.TRY_CLOSE) {
            LadsSettingsScreen26 menu = (LadsSettingsScreen26) screen;
            if (!menu.mouseClicked(event, false)) menu.closeFromMenuKey();
            menu.afterMouseAction();
            if (mc.gui.screen() == screen) return true;
            CONTROLLER.capture(physicalKey, 0);
            NativeFeatures.reset();
        }
        KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(event.button()), false);
        return true;
    }
}
