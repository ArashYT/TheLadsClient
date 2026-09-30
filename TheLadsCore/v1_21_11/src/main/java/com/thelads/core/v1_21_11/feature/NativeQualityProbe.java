package com.thelads.core.v1_21_11.feature;

import com.thelads.core.v1_21_11.feature.qa.mixin.KeyboardHandlerQaInvoker;
import com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/**
 * The 26.x native feature probe on 1.21.11, in the verified auto-world only: exercises transformed Minecraft methods through
 * real handlers and restores what it changes. 26.x's tooltip, chat-indicator and chained feature probes cover features this
 * version does not have yet; each 1.3.5 unit adds its checks here (26.x order) as it ports the feature.
 */
final class NativeQualityProbe {
    private static boolean done;
    private static int passed;
    private NativeQualityProbe() {}

    static boolean done() { return done; }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (done || !(Boolean.getBoolean("thelads.verifyNativeFeatures") || Boolean.getBoolean("thelads.verifyIntegrations"))
            || !NativeWorldVerification.worldReady() || mc.getEntityRenderDispatcher().camera == null) return;
        done = true;
        try {
            inputPipeline();
            LoggerFactory.getLogger("TheLadsCore").info("Lads native feature probe END: {} passed, 0 failed", passed);
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads native feature probe FAILED after {} checks", passed, failure);
        }
    }
    /** 26.x's Right Shift pipeline; without a physical input session, synthetic focus stands in inside the verified sandbox. */
    private static void inputPipeline() {
        Minecraft mc = Minecraft.getInstance();
        Screen previousScreen = mc.screen;
        boolean wasDown = NativeKeyBindings.MODULES.isDown();
        KeyEvent rightShift = new KeyEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, 54, GLFW.GLFW_MOD_SHIFT);
        var keyboard = (KeyboardHandlerQaInvoker) mc.keyboardHandler;
        long window = mc.getWindow().handle();
        int before = passed;
        NativeWorldVerification.syntheticInput(true);
        try {
            require(NativeKeyBindings.MODULES.matches(rightShift), "isolated QA retains default Right Shift binding");
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_PRESS, rightShift);
            Screen menu = mc.screen;
            require(menu instanceof LadsSettingsScreen12111, "Right Shift opens mods through real keyboard handler");
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_REPEAT, rightShift);
            require(mc.screen == menu, "native repeated press does not close menu");
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_RELEASE, rightShift);
            require(mc.screen == menu, "native release retains menu");
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_PRESS, rightShift);
            require(mc.screen == null, "second Right Shift closes to gameplay");
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_RELEASE, rightShift);
            ChatScreen chat = new ChatScreen("QA draft preserved", false);
            mc.setScreen(chat);
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_PRESS, rightShift);
            require(mc.screen == chat, "Right Shift preserves open chat");
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_RELEASE, rightShift);
            require(mc.screen == chat, "release preserves open chat");
            PauseScreen pause = new PauseScreen(true);
            mc.setScreen(pause);
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_PRESS, rightShift);
            require(mc.screen instanceof LadsSettingsScreen12111, "Right Shift opens mods from pause menu");
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_RELEASE, rightShift);
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_PRESS, rightShift);
            require(mc.screen == pause, "closing mods returns to original pause screen");
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_RELEASE, rightShift);
            require(!NativeKeyBindings.MODULES.isDown(), "menu press does not latch gameplay key state");
            LoggerFactory.getLogger("TheLadsCore").info("Lads native input pipeline END: {} passed, 0 failed (synthetic GLFW 344 through KeyboardHandler)", passed - before);
        } finally {
            keyboard.ladsQaKeyPress(window, GLFW.GLFW_RELEASE, rightShift);
            mc.setScreen(previousScreen);
            NativeKeyBindings.MODULES.setDown(wasDown);
            NativeWorldVerification.syntheticInput(false);
        }
    }
    private static void require(boolean result, String name) {
        if (!result) throw new IllegalStateException(name);
        passed++;
    }
}
