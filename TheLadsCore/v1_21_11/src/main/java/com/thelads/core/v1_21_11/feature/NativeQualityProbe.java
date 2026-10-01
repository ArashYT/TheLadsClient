package com.thelads.core.v1_21_11.feature;

import com.thelads.core.v1_21_11.feature.qa.mixin.KeyboardHandlerQaInvoker;
import com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111;
import com.thelads.core.client.BorderlessWindow;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_21_11.feature.qa.mixin.ChatQaAccessor;
import com.thelads.core.v1_21_11.feature.qa.mixin.WindowQaInvoker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.joml.Matrix3x2f;
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
            chatModule();
            borderless();
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
    /** The Chat module: timestamps, signing indicators, and an animated newest message that leaves the rest of the HUD in place. */
    private static void chatModule() {
        Minecraft mc = Minecraft.getInstance();
        var chat = mc.gui.getChat();
        var module = NativeQualityOfLife.module("Chat");
        var settings = ConfigManager.toJson();
        int before = passed;
        try {
            require(ModuleSupport.isBuiltIn("Chat") && ModuleSupport.isBuiltIn("BorderlessFullscreen"), "Chat and BorderlessFullscreen are connected");
            module.setEnabled(true);
            ((BoolOption) module.getOption("Timestamps")).set(true);
            ((BoolOption) module.getOption("Message Animations")).set(true);
            var original = Component.literal("Styled QA").withStyle(ChatFormatting.AQUA);
            chat.addMessage(original);
            GuiMessage message = ((ChatQaAccessor) chat).ladsQaMessages().get(0);
            require(message.content().getString().matches("\\[\\d{2}:\\d{2}\\] Styled QA"), "chat entry gets a timestamp");
            require(message.content().getSiblings().get(1).getStyle().equals(original.getStyle()), "original chat styling retained");
            var tag = GuiMessageTag.system();
            var line = new GuiMessage.Line(0, original.getVisualOrderText(), tag, true);
            var hide = (BoolOption) module.getOption("Hide Signing Indicators");
            hide.set(false);
            require(line.tag() == tag, "signing indicator shown with the option off");
            hide.set(true);
            require(line.tag() == null, "signing indicator hidden immediately");
            var graphics = new GuiGraphics(mc, new GuiRenderState(), 0, 0);
            var pose = new Matrix3x2f(graphics.pose());
            chat.addMessage(Component.literal("Lads chat animation QA"));
            chat.render(graphics, mc.font, mc.gui.getGuiTicks(), 0, 0, false, false);
            require(graphics.pose().equals(pose), "animating chat leaves the HUD pose unchanged for everything drawn after it");
            LoggerFactory.getLogger("TheLadsCore").info("Lads chat probe END: {} passed, 0 failed; pose after animated chat {} == before {}", passed - before, graphics.pose(), pose);
        } finally {
            ConfigManager.applyJson(settings);
        }
    }

    /** BorderlessFullscreen through the real window: toggleFullScreen, then setMode as the next frame's updateDisplay would. */
    private static void borderless() {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        var mode = (WindowQaInvoker) (Object) window;
        var module = NativeQualityOfLife.module("BorderlessFullscreen");
        boolean wasEnabled = module.isEnabled(), wasFullscreen = window.isFullscreen();
        Runnable toggle = () -> { window.toggleFullScreen(); mode.ladsQaSetMode(); };
        int before = passed;
        try {
            module.setEnabled(true);
            if (wasFullscreen) toggle.run();
            int width = window.getScreenWidth(), height = window.getScreenHeight();
            toggle.run();
            long handle = window.handle(), owner = GLFW.glfwGetWindowMonitor(handle);
            int[] px = {0}, py = {0}, pw = {0}, ph = {0}, mx = {0}, my = {0};
            GLFW.glfwGetWindowPos(handle, px, py);
            GLFW.glfwGetWindowSize(handle, pw, ph);
            long monitor = GLFW.glfwGetPrimaryMonitor();
            var monitors = GLFW.glfwGetMonitors();
            if (monitors != null) for (int i = 0; i < monitors.remaining(); i++) {
                int[] x = {0}, y = {0};
                GLFW.glfwGetMonitorPos(monitors.get(i), x, y);
                if (x[0] == px[0] && y[0] == py[0]) monitor = monitors.get(i);
            }
            var video = GLFW.glfwGetVideoMode(monitor);
            GLFW.glfwGetMonitorPos(monitor, mx, my);
            LoggerFactory.getLogger("TheLadsCore").info("Lads borderless window: glfwGetWindowMonitor == {}, decorated={}, window rect x={} y={} w={} h={}, monitor rect x={} y={} w={} h={}, framebuffer {}x{}",
                owner, GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_DECORATED), px[0], py[0], pw[0], ph[0], mx[0], my[0], video.width(), video.height(), window.getWidth(), window.getHeight());
            require(window.isFullscreen(), "fullscreen requested");
            require(owner == 0, "borderless is a desktop window, not monitor-owned fullscreen");
            require(GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_DECORATED) == GLFW.GLFW_FALSE, "native window has no decorations");
            require(px[0] == mx[0] && py[0] == my[0] && pw[0] == video.width() && ph[0] == video.height() + BorderlessWindow.EXTRA_HEIGHT,
                "borderless covers the monitor plus the capture-safe extra row");
            toggle.run();
            require(GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_DECORATED) == GLFW.GLFW_TRUE, "window decorations restored");
            require(window.getScreenWidth() == width && window.getScreenHeight() == height, "windowed dimensions restored");
            LoggerFactory.getLogger("TheLadsCore").info("Lads borderless probe END: {} passed, 0 failed", passed - before);
        } finally {
            if (window.isFullscreen() != wasFullscreen) toggle.run();
            module.setEnabled(wasEnabled);
            mc.resizeDisplay();
        }
    }
    private static void require(boolean result, String name) {
        if (!result) throw new IllegalStateException(name);
        passed++;
    }
}
