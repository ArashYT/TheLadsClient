package com.thelads.core.v1_21_11.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.ToggleSneakModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.v1_21_11.feature.qa.mixin.GameRendererQaInvoker;
import com.thelads.core.v1_21_11.feature.qa.mixin.KeyboardHandlerQaInvoker;
import com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Isolated auto-world QA of the 1.3.5 U1 menu access features through real screens, buttons and KeyboardHandler: Right Shift
 * on the title screen, the pause menu's "Lads Client" and Multiplayer rows (only the confirm dialog's "Stay in game": QA never
 * takes the save/disconnect path), typing and a slider drag in the Lads menu, and Zoom, Fullbright, ToggleSprint and
 * ToggleSneak against the FOV, lightmap and player input the game really uses. Every module, option and key is restored.
 */
final class NativeMenuAccessProbe {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private record Saved(boolean enabled, long modified, long opened, Map<Option, JsonElement> options) {}
    private interface Step { boolean run(Minecraft mc) throws Exception; }
    private static final List<Step> STEPS = List.of(NativeMenuAccessProbe::pauseMenu, NativeMenuAccessProbe::typing,
        NativeMenuAccessProbe::slider, NativeMenuAccessProbe::zoom, NativeMenuAccessProbe::fullbrightOn, NativeMenuAccessProbe::fullbrightOff,
        NativeMenuAccessProbe::fullbrightRestored, NativeMenuAccessProbe::sneakOn,
        NativeMenuAccessProbe::sneakHeld, NativeMenuAccessProbe::sneakReleased,
        NativeMenuAccessProbe::sprinting, NativeMenuAccessProbe::sprintReleased);
    private static final Map<Module, Saved> saved = new LinkedHashMap<>();
    private static boolean titleDone, finished, persisted, restored = true;
    private static int step, passed, dark;
    private static long ticks, wakeTick, wakeFrame;
    private static LadsSettingsScreen12111 menu;
    private NativeMenuAccessProbe() {}

    /** From NativeWorldVerification's title wait, before the QA world opens: Right Shift on the real title screen. */
    static void title() {
        if (titleDone) return;
        titleDone = true;
        Minecraft mc = Minecraft.getInstance();
        Screen title = mc.screen;
        NativeWorldVerification.syntheticInput(true);
        try {
            check(title instanceof TitleScreen, "the title screen is showing");
            tap(InputConstants.KEY_RSHIFT, GLFW.GLFW_MOD_SHIFT);
            // The tap's release is not part of the menu's decision; the press opened it and the menu is still open.
            check(mc.screen instanceof LadsSettingsScreen12111, "Right Shift through KeyboardHandler opens the Lads menu from the title screen");
            tap(InputConstants.KEY_RSHIFT, GLFW.GLFW_MOD_SHIFT);
            check(mc.screen == title, "a second Right Shift closes the Lads menu back to its title-screen parent");
        } catch (Throwable failure) {
            finished = true;
            LOGGER.error("Lads menu access probe FAILED at the title screen after {} checks", passed, failure);
        } finally {
            NativeWorldVerification.syntheticInput(false);
            if (mc.screen != title && mc.level == null) mc.setScreen(title);
        }
    }

    /** Every client tick; runs after the native feature probe, one step at a time in the verified QA world. */
    static void tick() {
        ticks++;
        if (finished || !titleDone || !NativeQualityProbe.done() || ticks < wakeTick || NativeWorldVerification.frames() < wakeFrame) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (step == 0) {
                if (!NativeWorldVerification.worldReady()) return;
                for (String name : List.of("Zoom", "Fullbright", "ToggleSprint", "ToggleSneak", "FPS")) save(module(name));
                restored = false;
            } else check(NativeWorldVerification.active() && mc.level != null && mc.player != null, "the isolated QA world is still open");
            if (!STEPS.get(step).run(mc)) return;
            if (++step < STEPS.size()) return;
            finished = true;
            restore();
            LOGGER.info("Lads menu access probe END: {} passed, 0 failed; title/pause/Lads menu through real screens and KeyboardHandler, "
                + "module toggles against the real FOV, lightmap and player input; settings restored", passed);
        } catch (Throwable failure) {
            finished = true;
            restore();
            LOGGER.error("Lads menu access probe FAILED after {} checks", passed, failure);
        }
    }

    private static boolean pauseMenu(Minecraft mc) {
        PauseScreen pause = new PauseScreen(true);
        mc.setScreen(pause);
        try {
            String multiplayer = Component.translatable("menu.multiplayer").getString();
            check(button(pause, "Lads Client") != null && button(pause, multiplayer) != null, "the pause menu has the Lads Client and Multiplayer rows");
            button(pause, "Lads Client").onPress(null);
            check(mc.screen instanceof LadsSettingsScreen12111, "Lads Client opens the Lads menu");
            ((LadsSettingsScreen12111) mc.screen).closeFromMenuKey();
            check(mc.screen == pause, "the Lads menu closes back to its pause-menu parent");
            button(pause, multiplayer).onPress(null);
            check(mc.screen instanceof ConfirmScreen, "Multiplayer asks before leaving the world, found " + mc.screen);
            Button stay = button(mc.screen, "Stay in game");
            check(stay != null, "the confirm dialog offers Stay in game");
            stay.onPress(null);
            check(mc.screen == pause && mc.level != null, "Stay in game returns to the pause menu with the world still open");
        } finally { if (mc.level != null) mc.setScreen(null); }
        menu = new LadsSettingsScreen12111(null);
        mc.setScreen(menu);
        return after(0, 2);
    }

    private static boolean typing(Minecraft mc) {
        var ui = menu.ui();
        check(mc.screen == menu && ui.controlBounds("search") != null, "the Lads menu rendered its search field");
        NativeWorldVerification.syntheticInput(true);
        try {
            tap(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL);
            check(ui.isEditingText(), "Ctrl+F through KeyboardHandler focuses the search field");
            type("zoom");
            check("zoom".equals(ui.getSearchQuery()), "characters typed through KeyboardHandler reach the search field");
            check(ui.visibleModuleNames().contains("Zoom") && !ui.visibleModuleNames().contains("FPS"), "the typed search filters the module catalog");
            tap(GLFW.GLFW_KEY_BACKSPACE, 0);
            check("zoo".equals(ui.getSearchQuery()), "Backspace edits the typed search");
            tap(GLFW.GLFW_KEY_ESCAPE, 0);
            check(!ui.isEditingText() && mc.screen == menu, "Escape leaves the search field and keeps the menu open");
        } finally { NativeWorldVerification.syntheticInput(false); }
        menu.openModule("FPS");
        return after(0, 2);
    }

    private static boolean slider(Minecraft mc) {
        Module fps = module("FPS");
        SliderOption size = (SliderOption) fps.getOption("Size");
        var track = menu.ui().controlBounds("option:Size");
        check(mc.screen == menu && track != null, "the FPS settings page rendered its Size slider");
        double y = track.y() + track.height() / 2.0, from = track.x() + 4, to = track.x() + track.width() - 4;
        menu.mouseClicked(new MouseButtonEvent(from, y, new MouseButtonInfo(0, 0)), false);
        check(size.getValue() == size.getMin(), "pressing the slider's left end selects its minimum");
        menu.mouseDragged(new MouseButtonEvent(to, y, new MouseButtonInfo(0, 0)), to - from, 0);
        menu.mouseReleased(new MouseButtonEvent(to, y, new MouseButtonInfo(0, 0)));
        persisted = true;
        check(size.getValue() == size.getMax(), "dragging the Size slider to its right end changes the value to its maximum");
        restore(fps);
        ConfigManager.save();
        check(size.save().equals(saved.get(fps).options().get(size)), "the Size value is restored and saved");
        menu.closeFromMenuKey();
        check(mc.screen == null, "the Lads menu closes back to gameplay");
        return true;
    }

    private static boolean zoom(Minecraft mc) {
        check(NativeWorldVerification.worldReady(), "gameplay is showing for the module checks");
        Module zoom = module("Zoom");
        ((BoolOption) zoom.getOption("Smooth Zoom")).set(false);
        int key = key(NativeKeyBindings.ZOOM);
        // Another mod may zoom on the same key (Essential's zoom is on C), so every check compares Lads Zoom on and off in one key state.
        NativeWorldVerification.syntheticInput(true);
        try {
            zoom.setEnabled(false);
            press(key, true);
            zoom.setEnabled(true); float pressedWhileOff = fov(mc); zoom.setEnabled(false);
            check(Math.abs(pressedWhileOff - fov(mc)) < 1e-4, "a zoom-key press while Zoom is off does not zoom");
            press(key, false);
            zoom.setEnabled(true);
            press(key, true);
            float on = fov(mc); zoom.setEnabled(false); float off = fov(mc); zoom.setEnabled(true);
            check(Math.abs(on - off * .25f) < 1e-3, "Zoom on: holding the zoom key narrows the rendered FOV to 25% (" + on + " of " + off + ")");
            press(key, false);
            float released = fov(mc); zoom.setEnabled(false);
            check(Math.abs(released - fov(mc)) < 1e-4, "releasing the zoom key ends the Lads zoom (" + released + ")");
        } finally { NativeWorldVerification.syntheticInput(false); }
        restore(zoom);
        // Each Fullbright state reaches the lightmap the way a player sees it: the game's own tick marks it for update
        // (BadOptimizations consults FullbrightLightmapHook there) and the next rendered frame recomputes it.
        module("Fullbright").setEnabled(false);
        return after(2, 2);
    }

    private static boolean fullbrightOn(Minecraft mc) {
        dark = lightmap(mc);
        module("Fullbright").setEnabled(true);
        return after(2, 2);
    }

    private static boolean fullbrightOff(Minecraft mc) {
        int bright = lightmap(mc);
        check(bright >= dark + 64, "Fullbright on raises the real lightmap (darkest texel " + dark + " -> " + bright + ", Lads gamma " + NativeFeatures.gamma(1) + ")");
        module("Fullbright").setEnabled(false);
        return after(2, 2);
    }

    private static boolean fullbrightRestored(Minecraft mc) {
        int again = lightmap(mc);
        check(Math.abs(again - dark) <= 8, "Fullbright off returns the lightmap to its own value (" + again + ", was " + dark + ")");
        restore(module("Fullbright"));
        return true;
    }

    private static boolean sneakOn(Minecraft mc) {
        // ToggleSneak in Toggle mode: one tap of the real sneak key latches crouching until the next tap.
        module("ToggleSneak").setEnabled(true);
        ((DropdownOption) module("ToggleSneak").getOption("Mode")).setIndex(0);
        NativeWorldVerification.syntheticInput(true);
        tap(key(mc.options.keyShift), 0);
        check(((ToggleSneakModule) module("ToggleSneak")).isToggled(), "ToggleSneak latches on a sneak-key tap");
        return after(2, 0);
    }

    private static boolean sneakHeld(Minecraft mc) {
        check(mc.player.isShiftKeyDown(), "the latched sneak reaches the player's real movement input");
        tap(key(mc.options.keyShift), 0);
        return after(2, 0);
    }

    private static boolean sneakReleased(Minecraft mc) {
        check(!((ToggleSneakModule) module("ToggleSneak")).isToggled() && !mc.player.isShiftKeyDown(), "a second sneak-key tap releases the latch");
        // ToggleSprint in Toggle mode: one tap of the real sprint key, then walking forward sprints without holding it.
        module("ToggleSprint").setEnabled(true);
        ((DropdownOption) module("ToggleSprint").getOption("Mode")).setIndex(0);
        tap(key(mc.options.keySprint), 0);
        check(((ToggleSprintModule) module("ToggleSprint")).isToggled(), "ToggleSprint latches on a sprint-key tap");
        press(key(mc.options.keyUp), true);
        return after(10, 0);
    }

    private static boolean sprinting(Minecraft mc) {
        check(mc.player.isSprinting(), "the latched sprint makes the player sprint while walking forward");
        press(key(mc.options.keyUp), false);
        tap(key(mc.options.keySprint), 0);
        return after(3, 0);
    }

    private static boolean sprintReleased(Minecraft mc) {
        check(!((ToggleSprintModule) module("ToggleSprint")).isToggled() && !mc.player.isSprinting(), "a second sprint-key tap ends the sprint");
        return true;
    }

    /**
     * The darkest lightmap texel (no block or sky light) the world renders with. Its GPU texture has no COPY_SRC usage for a
     * CommandEncoder readback, and 1.21.11 only has the OpenGL device, so this reads it with glGetTexImage.
     */
    private static int lightmap(Minecraft mc) {
        var texture = mc.gameRenderer.lightTexture().getTextureView().texture();
        check(GlStateManager._getInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING) == 0, "no pixel pack buffer is bound for the lightmap read");
        // Raw bind, then the previous binding back: mods that bind with raw GL can leave GlStateManager's cache stale.
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var texels = stack.malloc(texture.getWidth(0) * texture.getHeight(0) * 4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, ((GlTexture) texture).glId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texels);
            return Math.max(texels.get(0) & 255, Math.max(texels.get(1) & 255, texels.get(2) & 255));
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous); }
    }
    private static boolean after(int ticksLater, int framesLater) {
        wakeTick = ticks + ticksLater;
        wakeFrame = NativeWorldVerification.frames() + framesLater;
        return true;
    }
    private static float fov(Minecraft mc) {
        return ((GameRendererQaInvoker) mc.gameRenderer).ladsQaFov(mc.gameRenderer.getMainCamera(), 1, true);
    }
    private static int key(KeyMapping mapping) {
        var key = InputConstants.getKey(mapping.saveString());
        check(key.getType() == InputConstants.Type.KEYSYM, mapping.getName() + " is bound to a keyboard key");
        return key.getValue();
    }
    private static void press(int key, boolean down) {
        Minecraft mc = Minecraft.getInstance();
        ((KeyboardHandlerQaInvoker) mc.keyboardHandler).ladsQaKeyPress(mc.getWindow().handle(), down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE,
            new KeyEvent(key, GLFW.glfwGetKeyScancode(key), 0));
    }
    private static void tap(int key, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        var keyboard = (KeyboardHandlerQaInvoker) mc.keyboardHandler;
        var event = new KeyEvent(key, GLFW.glfwGetKeyScancode(key), modifiers);
        keyboard.ladsQaKeyPress(mc.getWindow().handle(), GLFW.GLFW_PRESS, event);
        keyboard.ladsQaKeyPress(mc.getWindow().handle(), GLFW.GLFW_RELEASE, event);
    }
    private static void type(String text) {
        Minecraft mc = Minecraft.getInstance();
        text.codePoints().forEach(c -> ((KeyboardHandlerQaInvoker) mc.keyboardHandler).ladsQaCharTyped(mc.getWindow().handle(), new CharacterEvent(c, 0)));
    }
    private static Button button(Screen screen, String label) {
        for (var child : screen.children()) if (child instanceof Button button && button.getMessage().getString().equals(label)) return button;
        return null;
    }
    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }
    private static void save(Module module) {
        var options = new LinkedHashMap<Option, JsonElement>();
        for (Option option : module.getOptions()) if (option.save() != null) options.put(option, option.save().deepCopy());
        saved.put(module, new Saved(module.isEnabled(), module.getLastModified(), module.getLastOpenedTime(), options));
    }
    private static void restore(Module module) {
        Saved state = saved.get(module);
        state.options().forEach(Option::load);
        module.setEnabled(state.enabled());
        module.setLastModified(state.modified());
        module.setLastOpenedTime(state.opened());
    }
    /** Idempotent: releases every key QA pressed, drops the toggle latches and restores each module it touched. */
    static void restore() {
        if (restored) return;
        restored = true;
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping mapping : List.of(NativeKeyBindings.ZOOM, mc.options.keyShift, mc.options.keySprint, mc.options.keyUp)) mapping.setDown(false);
        NativeFeatures.reset();
        NativeWorldVerification.syntheticInput(false);
        saved.keySet().forEach(NativeMenuAccessProbe::restore);
        if (persisted) ConfigManager.save();
        if (menu != null && mc.screen == menu && mc.level != null) mc.setScreen(null);
    }
    private static void check(boolean result, String description) {
        if (!result) throw new IllegalStateException("Menu access QA: " + description);
        passed++;
    }
}
