package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.ReconnectSettings;
import com.thelads.core.config.Option;
import com.thelads.core.v26_2.gui.ReconnectOptionsScreen26;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

/**
 * QA only, once at the title screen of an isolated run (thelads.verifyIntegrations): AutoReconnect's disconnect-screen wiring
 * with a mock target that only counts. No server or world is opened; the real reconnect is ServerFeaturesCapture's, in-world.
 */
final class NativeReconnectProbe {
    private static boolean done;
    private static int passed;
    private NativeReconnectProbe() {}

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (done || !Boolean.getBoolean("thelads.verifyIntegrations") || !(mc.gui.screen() instanceof TitleScreen title) || mc.getConnection() != null) return;
        done = true;
        var module = NativeReconnect.module();
        boolean enabled = module.isEnabled();
        long modified = module.getLastModified();
        var options = new LinkedHashMap<Option, com.google.gson.JsonElement>();
        module.getOptions().forEach(option -> options.put(option, option.save().deepCopy()));
        ReconnectSettings saved = NativeReconnect.settings();
        int[] connects = {0};
        try {
            module.setEnabled(true);
            module.initial.set(false);
            module.reasonMode.setIndex(0);
            ReconnectSettings lists = new ReconnectSettings();
            lists.retryDelays = new java.util.ArrayList<>(List.of(5));
            NativeReconnect.useSettings(lists);
            var session = NativeReconnect.session();
            session.begin("mock.invalid", () -> connects[0]++, mc.getUser().getProfileId());
            check(dialog(title, Component.literal("QA lost")) && NativeReconnect.retryButton() == null, "a target never joined gets no retry (Retry Initial Failures off)");
            module.initial.set(true);
            DisconnectedScreen screen = new DisconnectedScreen(title, Component.literal("Lads QA"), Component.literal("QA lost"));
            mc.gui.setScreen(screen);
            Button retry = NativeReconnect.retryButton(), cancel = NativeReconnect.cancelButton();
            check(retry != null && screen.children().contains(retry) && screen.children().contains(cancel), "the disconnect screen gets Reconnect and Cancel reconnect");
            check(retry.getMessage().getString().equals("Reconnect in 5s") && cancel.active, "an initial failure counts down when Retry Initial Failures is on");
            int widgets = screen.children().size();
            screen.resize(320, 240);
            check(screen.children().size() == widgets && session.counting(), "a resize re-adds the buttons once and keeps the countdown");
            press(InputConstants.KEY_ESCAPE);
            check(mc.gui.screen() == screen && !session.counting() && NativeReconnect.retryButton().getMessage().getString().equals("Reconnect"),
                "Escape through the game's key handler cancels the countdown and keeps the reason on screen");
            press(InputConstants.KEY_ESCAPE);
            check(mc.gui.screen() == screen, "with no countdown Escape does what vanilla does here (nothing)");
            NativeReconnect.retryButton().onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            check(connects[0] == 1, "Reconnect retries the target at once");

            session.begin("mock.invalid", () -> connects[0]++, mc.getUser().getProfileId());
            session.joined(null, lists, false, false, false, System.nanoTime());
            for (String kick : List.of("multiplayer.disconnect.kicked", "multiplayer.disconnect.banned", "multiplayer.disconnect.not_whitelisted")) {
                mc.gui.setScreen(new DisconnectedScreen(title, Component.literal("Lads QA"), Component.translatable(kick)));
                check(NativeReconnect.retryButton() != null && !session.counting(), "no countdown after " + kick);
            }
            mc.gui.setScreen(new DisconnectedScreen(title, Component.literal("Lads QA"), Component.translatable("disconnect.timeout")));
            check(session.counting(), "a timeout counts down");
            NativeReconnect.cancelButton().onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            check(!session.counting() && !NativeReconnect.cancelButton().active, "Cancel reconnect stops the countdown");
            check(connects[0] == 1, "cancelled countdowns never reconnect");

            var editor = new ReconnectOptionsScreen26(title);
            mc.gui.setScreen(editor);
            editor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("2, 4, 8");
            button(editor, "Save").onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            check(NativeReconnect.settings().retryDelays.equals(List.of(2, 4, 8)) && mc.gui.screen() == title, "the delays editor saves and returns");
            LoggerFactory.getLogger("TheLadsCore").info("Lads native reconnect probe END: {} checks passed, 0 failed; mock target, no server or world opened", passed);
        } catch (Throwable failed) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads native reconnect probe FAILED after {} checks", passed, failed);
        } finally {
            NativeReconnect.session().clear();
            try { NativeReconnect.replaceSettings(saved); } catch (RuntimeException ignored) { NativeReconnect.useSettings(saved); }
            options.forEach(Option::load);
            module.setEnabled(enabled);
            module.setLastModified(modified);
            mc.gui.setScreen(title);
        }
    }

    /** Shows a disconnect screen; true when it opened. */
    private static boolean dialog(Screen parent, Component reason) {
        Minecraft.getInstance().gui.setScreen(new DisconnectedScreen(parent, Component.literal("Lads QA"), reason));
        return Minecraft.getInstance().gui.screen() instanceof DisconnectedScreen;
    }

    /** A key press through KeyboardHandler, where Fabric's screen key events run. */
    private static void press(int key) throws ReflectiveOperationException {
        Minecraft mc = Minecraft.getInstance();
        Method keyPress = KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
        keyPress.setAccessible(true);
        keyPress.invoke(mc.keyboardHandler, mc.getWindow().handle(), InputConstants.PRESS, new KeyEvent(key, 0, 0));
        keyPress.invoke(mc.keyboardHandler, mc.getWindow().handle(), InputConstants.RELEASE, new KeyEvent(key, 0, 0));
    }

    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        passed++;
    }
}
