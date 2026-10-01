package com.thelads.core.v1_21_11.feature;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.gui.LadsPalette;
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
import com.thelads.core.v1_21_11.gui.FlashbackScreens;
import com.thelads.core.v1_21_11.gui.LadsSettingsScreen12111;
import com.thelads.core.v1_21_11.gui.SmoothScrollTarget;
import com.thelads.core.v1_21_11.gui.TitleExtrasScreen12111;
import com.thelads.core.v1_21_11.embedded.controlling.api.entries.IKeyEntry;
import com.thelads.core.v1_21_11.embedded.controlling.client.NewKeyBindsScreen;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.ColoredRectangleRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
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
    private static final List<Step> STEPS = List.of(NativeMenuAccessProbe::pauseOpen, NativeMenuAccessProbe::pauseChrome,
        NativeMenuAccessProbe::pauseMenu, NativeMenuAccessProbe::typing,
        NativeMenuAccessProbe::slider, NativeMenuAccessProbe::zoom, NativeMenuAccessProbe::fullbrightOn, NativeMenuAccessProbe::fullbrightOff,
        NativeMenuAccessProbe::fullbrightRestored, NativeMenuAccessProbe::sneakOn,
        NativeMenuAccessProbe::sneakHeld, NativeMenuAccessProbe::sneakReleased,
        NativeMenuAccessProbe::sprinting, NativeMenuAccessProbe::sprintReleased,
        NativeMenuAccessProbe::controlsOpen, NativeMenuAccessProbe::controlsSearch, NativeMenuAccessProbe::controlsKeybind,
        NativeMenuAccessProbe::smoothWheel, NativeMenuAccessProbe::smoothSettled, NativeMenuAccessProbe::packsOpen,
        NativeMenuAccessProbe::packsFilter);
    private static final Map<Module, Saved> saved = new LinkedHashMap<>();
    private static boolean titleDone, finished, persisted, restored = true;
    private static long titleFrame = Long.MAX_VALUE - 10;
    private static int step, passed, dark;
    private static long ticks, wakeTick, wakeFrame;
    private static LadsSettingsScreen12111 menu;
    // U4 (title, pause and GUI chrome): the screens the chrome steps drive across rendered frames.
    private static PauseScreen pause;
    private static KeyBindsScreen keys;
    private static KeyBindsList keyList;
    private static double scrollBefore, wheelStep;
    private static PackSelectionScreen packs;
    private NativeMenuAccessProbe() {}

    /** The title checks ran (passed or failed); NativeWorldVerification captures the title frame after them. */
    static boolean titleChecked() { return titleDone && NativeWorldVerification.frames() >= titleFrame + 3; }

    /** The pause menu the chrome steps keep open across frames, which the world runtime must not dismiss. */
    static boolean drives(Screen screen) { return screen != null && screen == pause && !finished; }

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
            titleChrome(mc, title);
        } catch (Throwable failure) {
            finished = true;
            LOGGER.error("Lads menu access probe FAILED at the title screen after {} checks", passed, failure);
        } finally {
            NativeWorldVerification.syntheticInput(false);
            if (mc.screen != title && mc.level == null) mc.setScreen(title);
            titleFrame = NativeWorldVerification.frames();
        }
    }

    /** Done (passed or failed); the U3 HUD probe starts after it so the two never change the same modules at once. */
    static boolean finished() { return finished; }

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
                + "module toggles against the real FOV, lightmap and player input; U4 title/More/Replays, pause redesign, controls search "
                + "and keybind scroll, smooth scroll, button surface and pack filter; settings restored", passed);
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

    // ---- U4: title, pause and GUI chrome (the 26.x NativeRequestProbe and Version133Probe checks, driven through real frames) ----

    /** On the real title screen: the Lads layout keeps six main actions, More holds the rest, Replays opens Flashback's browser. */
    private static void titleChrome(Minecraft mc, Screen title) {
        // The layout is arranged on the first draw or click; a click on no widget arranges the re-opened title.
        title.mouseClicked(new MouseButtonEvent(-1, -1, new MouseButtonInfo(0, 0)), false);
        // FancyMenu customization-overlay tools (Drippy's edit tab, shown while FancyMenu's overlay is on) stay where FancyMenu keeps them.
        var tools = visible(title).stream().filter(NativeMenuAccessProbe::overlayTool).toList();
        var main = visible(title).stream().filter(widget -> !overlayTool(widget)).toList();
        var labels = main.stream().map(widget -> widget.getMessage().getString()).toList();
        LOGGER.info("Lads menu access probe: the Lads title shows {} and leaves {} FancyMenu overlay tool(s) in place", labels, tools.size());
        for (String label : List.of("Lads Mods", "More...", Component.translatable("menu.singleplayer").getString(),
                Component.translatable("menu.multiplayer").getString(), Component.translatable("menu.options").getString(),
                Component.translatable("menu.quit").getString()))
            check(labels.contains(label), "the Lads title shows " + label + " " + labels);
        check(labels.size() == 6, "only the six main actions stay on the Lads title, found " + labels);
        check(title.children().stream().noneMatch(child -> child.getClass().getName().startsWith("gg.essential.")),
            "no Essential widget is left on the Lads title (Essential overlap fix)");
        apart(main, title, "title");
        button(title, "More...").onPress(null);
        check(mc.screen instanceof TitleExtrasScreen12111, "More... opens the Lads extras page, found " + mc.screen);
        LOGGER.info("Lads menu access probe: the title's More page holds {}", originals(mc.screen));
        check(originals(mc.screen).stream().noneMatch(original -> original.contains("(de.keksuccino.")),
            "More lists actions only: no FancyMenu artwork or customization-overlay tool");
        var extras = moreLabels(mc.screen);
        check(extras.contains("Accounts"), "More keeps the Accounts action " + extras);
        String flashback = Component.translatable("flashback.open_replays").getString();
        check(!FlashbackScreens.available() || extras.contains(flashback) || extras.contains("Replays"), "More offers Replays with Flashback installed " + extras);
        button(mc.screen, "Done").onPress(null);
        check(mc.screen == title, "Done returns to the title screen");
        if (!FlashbackScreens.available()) return;
        // Replays from the re-opened title's More page, clicked where it is drawn, as a player would.
        title.mouseClicked(new MouseButtonEvent(-1, -1, new MouseButtonInfo(0, 0)), false);
        click(title, button(title, "More..."));
        Screen more = mc.screen;
        Button replays = more instanceof TitleExtrasScreen12111 ? onPages(more, flashback, "Replays") : null;
        check(replays != null, "the re-opened More page has Replays");
        click(more, replays);
        check(mc.screen != null && mc.screen.getClass().getName().equals("com.moulberry.flashback.screen.select_replay.SelectReplayScreen"),
            "Replays opens Flashback's replay browser, found " + mc.screen);
        mc.setScreen(title);
    }

    /** Every label on every page of the More screen (26.x pages it), each page checked for overlaps on the way. */
    private static List<String> moreLabels(Screen more) {
        var labels = new ArrayList<String>();
        for (int page = 0; ; page++) {
            apart(more, "More page " + (page + 1));
            for (var widget : visible(more)) {
                String label = widget.getMessage().getString();
                if (!List.of("<", ">", "Done").contains(label)) labels.add(label);
            }
            Button next = button(more, ">");
            if (next == null || !next.active) return labels;
            next.onPress(null);
        }
    }
    /** The first action with one of the labels, turning More's pages forward until it is shown. */
    private static Button onPages(Screen more, String... labels) {
        while (true) {
            for (String label : labels) if (button(more, label) != null) return button(more, label);
            Button next = button(more, ">");
            if (next == null || !next.active) return null;
            next.onPress(null);
        }
    }
    /** QA evidence: the original widgets More was built from, with their owners (vanilla, Lads, mods, Essential). */
    private static List<String> originals(Screen more) {
        try {
            var field = more.getClass().getDeclaredField("actions");
            field.setAccessible(true);
            var names = new ArrayList<String>();
            for (Object widget : (List<?>) field.get(more))
                names.add(((AbstractWidget) widget).getMessage().getString() + " (" + widget.getClass().getName() + ")");
            return names;
        } catch (ReflectiveOperationException failure) { return List.of("unreadable: " + failure); }
    }
    /** Whether Essential's menu layer for this screen is currently in its overlay manager (reflection, QA only). */
    static boolean essentialOverlayShown(Screen screen) {
        if (!FabricLoader.getInstance().isModLoaded("essential")) return false;
        try {
            Object handler = screen.getClass().getMethod("essential$getProxyHandler").invoke(screen);
            Object layer = handler == null ? null : handler.getClass().getMethod("getLayer").invoke(handler);
            if (layer == null) return false;
            var layers = Class.forName("gg.essential.gui.overlay.OverlayManagerImpl").getDeclaredField("layers");
            layers.setAccessible(true);
            return ((List<?>) layers.get(null)).contains(layer);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Essential's overlay state could not be read", failure);
        }
    }

    private static boolean pauseOpen(Minecraft mc) {
        pause = new PauseScreen(true);
        mc.setScreen(pause);
        // The Lads pause layout is arranged when the menu first draws; Essential re-adds its menu layer once after init.
        return after(0, 3);
    }

    /** The redesigned pause menu after real frames: no report/feedback rows, extras relocated, a clean grid, Replays asks first. */
    private static boolean pauseChrome(Minecraft mc) {
        check(mc.screen == pause, "the pause menu is still open, found " + mc.screen);
        try {
            for (String key : List.of("menu.sendFeedback", "menu.reportBugs", "menu.playerReporting"))
                check(button(pause, Component.translatable(key).getString()) == null, "the Lads pause menu drops " + key);
            check(pause.children().stream().noneMatch(child -> child instanceof AbstractStringWidget), "the Lads logo replaces the Game Menu caption");
            check(pause.children().stream().noneMatch(child -> child.getClass().getName().startsWith("gg.essential.")),
                "Essential's pause widgets are relocated, not drawn over the Lads grid");
            check(button(pause, "Lads Client") != null && button(pause, Component.translatable("menu.multiplayer").getString()) != null,
                "the Lads Client and Multiplayer rows survive the relayout");
            check(!FlashbackScreens.available() || button(pause, "Replays") != null, "the pause menu has Replays with Flashback installed");
            apart(pause, "pause");
            check(!essentialOverlayShown(pause), "Essential's menu layer is not drawn over the Lads pause menu");
            Button extras = button(pause, "Essential & extras...");
            check(!FabricLoader.getInstance().isModLoaded("essential") || extras != null, "Essential's pause actions sit behind Essential & extras...");
            if (extras != null) {
                extras.onPress(null);
                check(mc.screen instanceof TitleExtrasScreen12111, "Essential & extras... opens the Lads extras page, found " + mc.screen);
                var labels = visible(mc.screen).stream().map(widget -> widget.getMessage().getString()).toList();
                LOGGER.info("Lads menu access probe: the pause extras page lists {}", labels);
                check(!FabricLoader.getInstance().isModLoaded("essential") || labels.contains("Essential settings"),
                    "Essential's Settings action is relocated as a Lads button " + labels);
                button(mc.screen, "Done").onPress(null);
                check(mc.screen == pause, "Done returns to the pause menu");
            }
            Button replays = button(pause, "Replays");
            if (replays != null) {
                replays.onPress(null);
                check(mc.screen instanceof ConfirmScreen, "Replays asks before leaving the world, found " + mc.screen);
                button(mc.screen, "Stay in game").onPress(null);
                check(mc.screen == pause && mc.level != null, "Stay in game keeps the world and the pause menu");
            }
        } finally { if (mc.level != null) mc.setScreen(null); }
        return true;
    }

    private static boolean controlsOpen(Minecraft mc) {
        mc.setScreen(new KeyBindsScreen(null, mc.options));
        check(mc.screen instanceof NewKeyBindsScreen, "Controls opens the embedded Controlling screen, found " + mc.screen);
        keys = (KeyBindsScreen) mc.screen;
        check(keys.children().stream().filter(child -> child instanceof EditBox).count() == 1, "exactly one controls search box (Controlling's)");
        keyList = keys.children().stream().filter(child -> child instanceof KeyBindsList).map(child -> (KeyBindsList) child).findFirst().orElseThrow();
        return after(0, 2);
    }

    private static boolean controlsSearch(Minecraft mc) {
        check(mc.screen == keys, "the controls screen is still open, found " + mc.screen);
        var search = keys.children().stream().filter(child -> child instanceof EditBox).map(child -> (EditBox) child).findFirst().orElseThrow();
        int total = keyList.children().size();
        keys.mouseClicked(new MouseButtonEvent(search.getX() + 8, search.getY() + 8, new MouseButtonInfo(0, 0)), false);
        NativeWorldVerification.syntheticInput(true);
        try { type("Lads"); } finally { NativeWorldVerification.syntheticInput(false); }
        check("Lads".equals(search.getValue()), "mouse focus and characters typed through KeyboardHandler reach controls search");
        check(!keyList.children().isEmpty() && keyList.children().size() < total, "controls search filters the real bindings");
        search.setValue("no_control_should_match_this_309127");
        check(keyList.children().isEmpty(), "controls search empty state");
        search.setValue("");
        check(keyList.children().size() == total, "clearing search restores every binding");
        search.setValue("category:lads");
        check(hasKey(NativeKeyBindings.MODULES) && keyList.children().stream().allMatch(row -> row instanceof IKeyEntry),
            "category: search finds the Lads menu key and lists bindings without headings");
        search.setValue("");
        Button unbound = button(keys, "Show Unbound");
        check(unbound != null && button(keys, "Show Conflicts") != null, "Controlling's Show Unbound and Show Conflicts filters are present");
        unbound.onPress(null);
        check("Show All".equals(unbound.getMessage().getString()) && keyList.children().stream().allMatch(row -> row instanceof IKeyEntry entry && entry.getKey().isUnbound()),
            "Show Unbound lists only unbound keys");
        unbound.onPress(null);
        check(keyList.children().size() == total, "Show All lists every binding again");
        keyList.setScrollAmount(Math.min(260, keyList.maxScrollAmount()));
        scrollBefore = keyList.scrollAmount();
        check(scrollBefore > 0, "the controls regression fixture is scrolled down");
        // Rendered frames place the rows at their scrolled positions.
        return after(0, 2);
    }

    /** The 1.3.4 fix: a click on a binding's button, and the key pressed for it, leave the list where it was scrolled. */
    private static boolean controlsKeybind(Minecraft mc) {
        check(mc.screen == keys && keyList.scrollAmount() == scrollBefore, "the controls list stays where it was scrolled");
        keys.mouseClicked(new MouseButtonEvent(1, 1, new MouseButtonInfo(0, 0)), false);
        check(keyList.scrollAmount() == scrollBefore, "an ordinary controls click keeps the scroll position");
        KeyMapping key = null;
        Button change = null;
        // A row drawn inside the viewport after the scroll: its change button was placed by that frame (rows scrolled out of view
        // keep the button position of the last frame that drew them).
        for (var row : keyList.children())
            if (row instanceof IKeyEntry entry && row.getY() >= keyList.getY() + 30 && row.getY() + row.getHeight() <= keyList.getBottom() - 30
                && row.children().getFirst() instanceof Button button && button.getY() == row.getY()) { key = entry.getKey(); change = button; break; }
        check(change != null, "a binding row is fully visible in the scrolled list");
        var old = InputConstants.getKey(key.saveString());
        try {
            click(keys, change);
            check(keys.selectedKey == key, "clicking a binding's button selects it for a new key");
            check(keyList.scrollAmount() == scrollBefore, "clicking a binding to change it keeps the controls scroll position");
            NativeWorldVerification.syntheticInput(true);
            try { tap(GLFW.GLFW_KEY_F9, 0); } finally { NativeWorldVerification.syntheticInput(false); }
            check(keys.selectedKey == null && "key.keyboard.f9".equals(key.saveString()), "the key pressed through KeyboardHandler is assigned");
            check(keyList.scrollAmount() == scrollBefore, "assigning a key keeps the controls scroll position");
        } finally {
            key.setKey(old);
            KeyMapping.resetMapping();
            keyList.resetMappingAndUpdateButtons();
            mc.options.save();
        }
        check(old.equals(InputConstants.getKey(key.saveString())), "the binding is restored");
        return true;
    }

    /** Smooth scrolling (26.x Version133Probe): the wheel sets a target, frames ease toward it, direct positioning stays immediate. */
    private static boolean smoothWheel(Minecraft mc) throws Exception {
        check(mc.screen == keys, "the controls screen is still open, found " + mc.screen);
        keyList.setScrollAmount(0);
        keyList.mouseScrolled(keyList.getX() + 10, keyList.getY() + 10, 0, -3);
        check(keyList.scrollAmount() == 0, "the wheel stores a target without jumping");
        var frame = AbstractScrollArea.class.getDeclaredField("ladsFrame");
        frame.setAccessible(true);
        frame.setLong(keyList, System.nanoTime() - 25_000_000L);
        ((SmoothScrollTarget) keyList).ladsAdvanceScroll();
        wheelStep = keyList.scrollAmount();
        check(wheelStep > 0 && wheelStep < 30, "one frame eases part of the way to the wheel target (" + wheelStep + " of 30)");
        return after(20, 3);
    }

    private static boolean smoothSettled(Minecraft mc) {
        check(keyList.scrollAmount() == 30, "rendered frames finish easing to the wheel target (" + keyList.scrollAmount() + ")");
        keyList.setScrollAmount(0);
        check(keyList.scrollAmount() == 0, "direct scrollbar/navigation positioning remains immediate");
        buttonSurface(mc);
        keys.resize(keys.width, keys.height);
        check(keys.children().stream().filter(child -> child instanceof EditBox).count() == 1, "a resize leaves exactly one search field");
        mc.setScreen(null);
        return true;
    }

    private static boolean packsOpen(Minecraft mc) {
        packs = new PackSelectionScreen(mc.getResourcePackRepository(), repository -> {}, mc.getResourcePackDirectory(), Component.literal("Resource packs"));
        mc.setScreen(packs);
        return after(0, 2);
    }

    /** The resource-pack filter and sort on the real pack screen, next to Resourcify's own buttons. */
    private static boolean packsFilter(Minecraft mc) throws Exception {
        check(mc.screen == packs, "the resource pack screen is still open, found " + mc.screen);
        try {
            Button version = button(packs, "Version: all"), sort = button(packs, "Sort: default");
            check(version != null && sort != null, "the pack screen has the Version and Sort buttons");
            for (Button ours : List.of(version, sort))
                for (var other : visible(packs))
                    check(other == ours || !(ours.getX() < other.getRight() && ours.getRight() > other.getX() && ours.getY() < other.getBottom() && ours.getBottom() > other.getY()),
                        ours.getMessage().getString() + " does not overlap " + other.getClass().getSimpleName() + " '" + other.getMessage().getString() + "'");
            var lists = packs.children().stream().filter(child -> child instanceof TransferableSelectionList).map(child -> (TransferableSelectionList) child)
                .sorted(java.util.Comparator.comparingInt(TransferableSelectionList::getX)).toList();
            check(lists.size() == 2, "the available and selected pack lists are shown");
            var available = lists.get(0);
            var selected = lists.get(1);
            var all = packEntries(available);
            var chosen = ids(packEntries(selected)); // the model hands out new entry objects on every fill
            version.onPress(null);
            var compatible = packEntries(available);
            check("Version: compatible".equals(version.getMessage().getString()) && compatible.stream().allMatch(entry -> entry.getCompatibility().isCompatible()),
                "Version: compatible lists only compatible packs");
            version.onPress(null);
            var incompatible = packEntries(available);
            check("Version: incompatible".equals(version.getMessage().getString()) && incompatible.stream().noneMatch(entry -> entry.getCompatibility().isCompatible()),
                "Version: incompatible lists only incompatible packs");
            check(compatible.size() + incompatible.size() == all.size(), "the two version filters split the " + all.size() + " available packs");
            check(ids(packEntries(selected)).equals(chosen), "selected packs are never filtered out");
            version.onPress(null);
            check("Version: all".equals(version.getMessage().getString()) && packEntries(available).size() == all.size(), "Version: all lists every pack again");
            sort.onPress(null);
            var titles = packEntries(available).stream().map(entry -> entry.getTitle().getString()).toList();
            var sorted = new ArrayList<>(titles);
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            check("Sort: name".equals(sort.getMessage().getString()) && titles.equals(sorted), "Sort: name orders the available packs by name");
            check(ids(packEntries(selected)).equals(chosen), "sorting keeps the selected packs in precedence order");
            sort.onPress(null);
            check("Sort: default".equals(sort.getMessage().getString()), "Sort: default restores the pack order");
            LOGGER.info("Lads menu access probe: pack filter split {} available packs into {} compatible and {} incompatible; {} selected",
                all.size(), compatible.size(), incompatible.size(), chosen.size());
        } finally {
            packs.onClose();
            if (mc.level != null) mc.setScreen(null);
        }
        return true;
    }

    /** Buttons draw the Lads surface (LadsPalette.CARD) in place of the vanilla sprite, through a real GUI render state. */
    private static void buttonSurface(Minecraft mc) {
        var state = new GuiRenderState();
        var button = Button.builder(Component.literal("QA"), pressed -> {}).bounds(4, 4, 100, 20).build();
        button.render(new GuiGraphics(mc, state, 0, 0), -1, -1, 0);
        boolean[] sprite = {false}, surface = {false};
        state.forEachElement(element -> {
            sprite[0] |= element instanceof BlitRenderState;
            surface[0] |= element instanceof ColoredRectangleRenderState rectangle && rectangle.col1() == LadsPalette.CARD;
        }, GuiRenderState.TraverseRange.ALL);
        check(surface[0] && !sprite[0], "buttons draw the Lads surface instead of the vanilla button sprite");
    }

    private static List<PackSelectionModel.Entry> packEntries(TransferableSelectionList list) throws ReflectiveOperationException {
        var entries = new ArrayList<PackSelectionModel.Entry>();
        for (var row : list.children()) {
            if (!(row instanceof TransferableSelectionList.PackEntry)) continue;
            for (var field : TransferableSelectionList.PackEntry.class.getDeclaredFields())
                if (PackSelectionModel.Entry.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    entries.add((PackSelectionModel.Entry) field.get(row));
                }
        }
        return entries;
    }
    private static List<String> ids(List<PackSelectionModel.Entry> entries) { return entries.stream().map(PackSelectionModel.Entry::getId).toList(); }
    private static boolean hasKey(KeyMapping mapping) {
        return keyList.children().stream().anyMatch(row -> row instanceof IKeyEntry entry && entry.getKey() == mapping);
    }
    private static List<AbstractWidget> visible(Screen screen) {
        var widgets = new ArrayList<AbstractWidget>();
        for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible) widgets.add(widget);
        return widgets;
    }
    /** FancyMenu's customization-overlay tools, which the Lads title leaves to FancyMenu (TitleScreenMixin.ladsOverlayTool). */
    private static boolean overlayTool(AbstractWidget widget) {
        return widget.getClass().getName().startsWith("de.keksuccino.") && !widget.getClass().getName().endsWith(".RendererWidget");
    }
    /** One check per screen: every visible widget is inside the screen and none overlaps another. */
    private static void apart(Screen screen, String where) { apart(visible(screen), screen, where); }
    private static void apart(List<AbstractWidget> widgets, Screen screen, String where) {
        var problems = new ArrayList<String>();
        for (int i = 0; i < widgets.size(); i++) {
            var a = widgets.get(i);
            if (a.getX() < 0 || a.getY() < 0 || a.getRight() > screen.width || a.getBottom() > screen.height) problems.add(a.getMessage().getString() + " outside");
            for (int j = i + 1; j < widgets.size(); j++) {
                var b = widgets.get(j);
                if (a.getX() < b.getRight() && a.getRight() > b.getX() && a.getY() < b.getBottom() && a.getBottom() > b.getY())
                    problems.add(a.getMessage().getString() + " over " + b.getMessage().getString());
            }
        }
        check(problems.isEmpty(), where + ": " + widgets.size() + " visible widgets inside the screen, none overlapping " + problems);
    }
    /** A left click where the widget is drawn, through the screen's own mouse handling. */
    private static void click(Screen screen, AbstractWidget widget) {
        screen.mouseClicked(new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, new MouseButtonInfo(0, 0)), false);
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
        if (mc.level != null && mc.screen != null && (mc.screen == pause || mc.screen == keys || mc.screen == packs || mc.screen instanceof ConfirmScreen
            || mc.screen instanceof TitleExtrasScreen12111)) mc.setScreen(null);
    }
    private static void check(boolean result, String description) {
        if (!result) throw new IllegalStateException("Menu access QA: " + description);
        passed++;
    }
}
