package com.thelads.core.v1_21_1.feature;

import com.thelads.core.v1_21_1.gui.LadsSettingsScreen121;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/**
 * The 26.x native feature probe on 1.21.1, in the verified auto-world only: exercises transformed Minecraft methods through
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
        long window = mc.getWindow().getWindow();
        java.util.function.IntConsumer rightShift = action -> mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_RIGHT_SHIFT, 54, action, GLFW.GLFW_MOD_SHIFT);
        int before = passed;
        NativeWorldVerification.syntheticInput(true);
        try {
            require(NativeKeyBindings.MODULES.matches(GLFW.GLFW_KEY_RIGHT_SHIFT, 54), "isolated QA retains default Right Shift binding");
            rightShift.accept(GLFW.GLFW_PRESS);
            Screen menu = mc.screen;
            require(menu instanceof LadsSettingsScreen121, "Right Shift opens mods through real keyboard handler");
            rightShift.accept(GLFW.GLFW_REPEAT);
            require(mc.screen == menu, "native repeated press does not close menu");
            rightShift.accept(GLFW.GLFW_RELEASE);
            require(mc.screen == menu, "native release retains menu");
            rightShift.accept(GLFW.GLFW_PRESS);
            require(mc.screen == null, "second Right Shift closes to gameplay");
            rightShift.accept(GLFW.GLFW_RELEASE);
            ChatScreen chat = new ChatScreen("QA draft preserved");
            mc.setScreen(chat);
            rightShift.accept(GLFW.GLFW_PRESS);
            require(mc.screen == chat, "Right Shift preserves open chat");
            rightShift.accept(GLFW.GLFW_RELEASE);
            require(mc.screen == chat, "release preserves open chat");
            PauseScreen pause = new PauseScreen(true);
            mc.setScreen(pause);
            rightShift.accept(GLFW.GLFW_PRESS);
            require(mc.screen instanceof LadsSettingsScreen121, "Right Shift opens mods from pause menu");
            rightShift.accept(GLFW.GLFW_RELEASE);
            rightShift.accept(GLFW.GLFW_PRESS);
            require(mc.screen == pause, "closing mods returns to original pause screen");
            rightShift.accept(GLFW.GLFW_RELEASE);
            require(!NativeKeyBindings.MODULES.isDown(), "menu press does not latch gameplay key state");
            LoggerFactory.getLogger("TheLadsCore").info("Lads native input pipeline END: {} passed, 0 failed (synthetic GLFW 344 through KeyboardHandler)", passed - before);
        } finally {
            rightShift.accept(GLFW.GLFW_RELEASE);
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
