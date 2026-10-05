package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonElement;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v26_2.gui.DraggableHudScreen26;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.slf4j.LoggerFactory;

/**
 * Explicit isolated-world QA. Real native events on the real editor screen, aimed through the preview's game-to-screen mapping;
 * fixture changes never save to disk. Screenshots (native-hud-*.png): the default view, Show disabled, a drag in progress, a plain
 * drop that docks without grouping, a Shift drop that groups, and the same layout in game after the editor closed.
 */
final class NativeHudEditorProbe implements AutoCloseable {
    private record ModuleState(boolean enabled, long modified, Map<Option, JsonElement> options) {}
    private final HudSettings settings = HudSettings.getInstance();
    private final Map<String, int[]> positions = new HashMap<>();
    private final Map<HudElement, int[]> elementPositions = new LinkedHashMap<>();
    private final Map<Module, ModuleState> modules = new LinkedHashMap<>();
    private final List<Set<String>> groups = new ArrayList<>();
    private Set<String> locked;
    private DraggableHudScreen controller;
    private DraggableHudScreen26 screen;
    private int stage, passed, saves;
    private long nextStep, readyAt;
    private boolean restored, inGame;
    private Rect beforeCps, beforeDay;
    private String shot;
    private int shotFrames;
    private boolean shotTaken;
    private Throwable shotFailure;
    private static final Set<String> PAIR = Set.of("CPS", "Day");
    private static final List<String> FIXTURE = List.of("CPS", "Day", "FPS", "Health");
    private static final int STAGES = 15;

    DraggableHudScreen26 open() {
        check(NativeWorldVerification.worldReady(), "strict isolated QA world guard");
        settings.getPositions().forEach((name, value) -> positions.put(name, value.clone()));
        settings.getGroups().forEach(group -> groups.add(Set.copyOf(group)));
        locked = Set.copyOf(settings.getLocked());
        for (HudElement element : HudManager.getInstance().getElements()) {
            elementPositions.put(element, new int[] {element.getX(), element.getY()});
            Module module = ModuleManager.getInstance().getModule(element.getModuleName());
            if (module == null) continue;
            var options = new LinkedHashMap<Option, JsonElement>();
            for (Option option : module.getOptions()) {
                JsonElement value = option.save();
                if (value != null) options.put(option, value.deepCopy());
            }
            modules.put(module, new ModuleState(module.isEnabled(), module.getLastModified(), options));
        }
        try {
            var menu = new LadsSettingsScreen();
            var names = menu.visibleModuleNames();
            var expected = ModuleManager.getInstance().getModules().stream()
                .map(Module::getName).filter(ModuleSupport::isBuiltIn).collect(java.util.stream.Collectors.toSet());
            check(Set.copyOf(names).equals(expected), "native menu contains exactly registered Lads implementations");
            check(names.size() >= 40, "native menu retains the implemented 26.2 catalog");
            for (Module module : ModuleManager.getInstance().getModules()) {
                if (!ModuleSupport.isBuiltIn(module.getName())) {
                    menu.setSearchQuery(module.getName());
                    check(menu.visibleModuleNames().stream().allMatch(ModuleSupport::isBuiltIn), "external search stays native: " + module.getName());
                }
            }
            settings.getPositions().clear();
            settings.replaceGroups(List.of());
            settings.replaceLocked(Set.of());
            for (Module module : modules.keySet()) {
                boolean fixture = FIXTURE.contains(module.getName());
                module.setEnabled(fixture);
                if (fixture) module.getOptions().forEach(Option::reset);
            }
            size("CPS", 150); size("Day", 125);
            settings.setPosition("CPS", 12, 42);
            settings.setPosition("Day", 12, 78);
            settings.setPosition("Health", 12, 112);
            settings.setPosition("FPS", 10000, 10);
            controller = new DraggableHudScreen(() -> saves++);
            screen = new DraggableHudScreen26(null, controller);
            nextStep = System.nanoTime() + 300_000_000L;
            return screen;
        } catch (RuntimeException failure) { close(); throw failure; }
    }

    void tick() {
        if (shotFailure != null) throw new IllegalStateException("HUD editor QA screenshot failed", shotFailure);
        if (stage >= STAGES || shot != null || System.nanoTime() < nextStep) return;
        if (!inGame && controller.boundsFor("CPS") == null) return;
        nextStep = System.nanoTime() + 300_000_000L;
        var mc = Minecraft.getInstance();
        // A button that changes what is listed or previewed is pressed last in a stage: its bounds exist after the next frame.
        switch (stage++) {
            case 0 -> {
                Rect cps = bounds("CPS"), fps = bounds("FPS"), preview = controller.previewBounds();
                check(cps.width() >= Math.ceil((mc.font.width("CPS: 0 | 0") + 12) * 1.5), "first-frame scaled CPS text fits its measured bounds");
                check(fps.right() <= screen.width && fps.x() >= 0, "first-frame FPS edge clamp uses its measured width");
                check(controller.listedNames().equals(FIXTURE), "the list shows only switched-on HUDs by default: " + controller.listedNames());
                check(Math.abs(preview.width() / (double) preview.height() - screen.width / (double) screen.height) < 0.02
                    && preview.right() <= screen.width && preview.bottom() <= screen.height, "the preview keeps the game's aspect inside the screen: " + preview);
                check(controller.controls().stream().map(DraggableHudScreen.Control::id).toList()
                    .containsAll(List.of("group", "ungroup", "lock", "snap", "done", "search", "previews", "colors", "reset")), "visible editing controls");
                check(controller.controls().stream().noneMatch(c -> c.bounds().intersects(preview)), "no control covers the preview");
                shot("hud-editor-default");
            }
            case 1 -> {
                button("previews");
                check(controller.isShowingAll() && controller.listedNames().size() > FIXTURE.size()
                    && controller.listedNames().contains("Memory"), "Show disabled lists switched-off HUDs");
                shot("hud-editor-all");
            }
            case 2 -> {
                check(controller.boundsFor("Memory") != null, "Show disabled previews switched-off HUDs");
                button("previews");
                check(!controller.isShowingAll() && controller.listedNames().equals(FIXTURE), "Show disabled off lists switched-on HUDs again");
            }
            case 3 -> {
                check(controller.boundsFor("Memory") == null, "and previews only them");
                click(bounds("CPS"), 0); click(bounds("Day"), 2);
                check(controller.selectedNames().equals(PAIR), "native Ctrl-click selects two HUDs");
                check(controller.toggleBoundsFor("CPS") != null && controller.settingsBoundsFor("CPS") != null, "the CPS row has a switch and a settings gear");
                key(71, 2);
                check(PAIR.equals(settings.getGroupMembers("CPS")), "native Ctrl-G creates a persistent group");
            }
            case 4 -> {
                beforeCps = bounds("CPS"); beforeDay = bounds("Day");
                check(Math.abs((beforeCps.x()+beforeCps.width()/2)-(beforeDay.x()+beforeDay.width()/2))<=1, "group centers align with mixed module scales");
                check(beforeDay.y()==beforeCps.bottom()+2,"group stack packs rows without overlap");
                drag(beforeCps, 10000, -10000, 0);
                check(relative(bounds("CPS"), bounds("Day"), beforeCps, beforeDay), "group border drag preserves both offsets");
            }
            case 5 -> {
                Rect cps = bounds("CPS"), day = bounds("Day");
                check(relative(cps, day, beforeCps, beforeDay), "next completed render preserves grouped offsets");
                check(cps.x() >= 0 && day.x() >= 0 && Math.max(cps.right(), day.right()) == screen.width
                    && Math.min(cps.y(), day.y()) == 0, "whole group clamps to viewport edges: CPS="+cps+", Day="+day+", viewport="+screen.width+"x"+screen.height+", members="+settings.getGroupMembers("CPS"));
                check(saved("CPS", cps) && saved("Day", day), "all grouped positions match rendered coordinates after save");
                button("lock");
                check(settings.isLocked("CPS") && settings.isLocked("Day"), "Lock position applies to the group");
                beforeCps = cps; beforeDay = day;
                drag(cps, -80, 30, 0); key(263, 0);
                check(bounds("CPS").equals(cps) && bounds("Day").equals(day), "locked group rejects mouse drag and arrow movement");
                // Clear selection on the open preview, then select the locked group again.
                click(new Rect(screen.width / 2, screen.height / 2 + 30, 1, 1), 0);
                check(controller.selectedNames().isEmpty(), "a click on the open preview clears the selection");
                click(cps, 0);
                check(controller.selectedNames().equals(PAIR), "locked group remains selectable for unlocking");
            }
            case 6 -> {
                button("unlock");
                check(!settings.isLocked("CPS") && !settings.isLocked("Day"), "Unlock releases both group members");
                key(263, 0); key(264, 1);
                check(bounds("CPS").x() == beforeCps.x() - 1 && bounds("CPS").y() == beforeCps.y() + 10,
                    "native arrow and Shift-arrow nudge by one and ten GUI pixels");
                check(relative(bounds("CPS"), bounds("Day"), beforeCps, beforeDay), "keyboard group movement preserves spacing");
                key(71, 3);
                check(settings.getGroupMembers("CPS") == null && settings.getGroupMembers("Day") == null, "Ctrl-Shift-G ungroups");
                click(bounds("CPS"), 0);
                check(controller.selectedNames().equals(Set.of("CPS")), "ungrouped HUD selects independently");
                click(bounds("Day"), 2);
                var b = bounds("CPS");
                screen.mouseClicked(new MouseButtonEvent(controller.screenX(b.x()+1), controller.screenY(b.y()+1), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0)), false);
                check(controller.selectedNames().equals(PAIR), "right-click preserves multi-selection for grouping");
            }
            case 7 -> {
                var group = controller.contextControls().stream().filter(c -> c.id().equals("group")).findFirst().orElseThrow();
                check(group.enabled(), "context Group is enabled for two selected modules"); clickScreen(group.bounds());
                check(PAIR.equals(settings.getGroupMembers("Day")), "right-click Group creates the centered stack");
                key(71, 0); // Snapping off for an exact free drag.
                Rect cps = bounds("CPS");
                drag(cps, 44 - cps.x(), 58 - cps.y(), 0);
                check(bounds("CPS").x() == 44 && bounds("CPS").y() == 58, "free drag keeps pointer and saved GUI coordinates aligned: " + bounds("CPS"));
                check(saved("CPS", bounds("CPS")) && saved("Day", bounds("Day")), "final group positions are persisted together");
                check(saves >= 7, "edit operations invoke the persistence owner");
                key(71, 0); // Snapping back on.
                clickScreen(controller.toggleBoundsFor("FPS"));
                check(!ModuleManager.getInstance().getModule("FPS").isEnabled() && !controller.listedNames().contains("FPS"),
                    "the FPS row switch disables the module and its row leaves the default list");
                button("previews");
            }
            case 8 -> {
                check(controller.boundsFor("FPS") != null && controller.toggleBoundsFor("FPS") != null, "Show disabled lists FPS again to switch it on");
                clickScreen(controller.toggleBoundsFor("FPS"));
                check(ModuleManager.getInstance().getModule("FPS").isEnabled(), "the row switch turns FPS back on");
                button("previews");
            }
            case 9 -> {
                // A plain drop: Health dragged until it docks under the CPS + Day stack, left mid-drag for the capture.
                Rect day = bounds("Day"), health = bounds("Health");
                press(health, 0); moveTo(health, day.x() + 1 - health.x(), day.bottom() + 2 - health.y());
                check(controller.isDragging() && bounds("Health").y() == day.bottom() && bounds("Health").x() == day.x(),
                    "snapping docks Health under Day during the drag: " + bounds("Health") + " under " + day);
                shot("hud-editor-dragging");
            }
            case 10 -> {
                Rect day = bounds("Day");
                releaseAt(bounds("Health"), 0);
                check(settings.getGroupMembers("Health") == null && PAIR.equals(settings.getGroupMembers("Day")), "a plain drop docks Health without grouping it");
                check(bounds("Health").y() == day.bottom() && saved("Health", bounds("Health")), "the docked drop is saved where it shows: " + bounds("Health"));
                shot("hud-editor-plain-drop");
            }
            case 11 -> {
                // The same drop with Shift held from press to release: Health joins the stack's group.
                Rect health = bounds("Health");
                press(health, 1); moveTo(health, 0, 30); moveTo(health, 0, 0); releaseAt(health, 1);
                check(Set.of("CPS", "Day", "Health").equals(settings.getGroupMembers("Health")), "a Shift drop groups Health with the stack: " + settings.getGroupMembers("Health"));
                click(bounds("Health"), 0); // selected, so the capture shows the group outline
                shot("hud-editor-shift-group");
            }
            case 12 -> {
                // The edited layout in game: the editor closes; the next frames draw the Lads HUD itself.
                // FPS keeps its saved 10000,10 and is only clamped on screen, in game as in the preview.
                for (String name : List.of("CPS", "Day", "Health")) check(saved(name, bounds(name)), name + " is saved where the preview shows it");
                LoggerFactory.getLogger("TheLadsCore").info("Lads HUD editor layout before the in-game capture: CPS {} Day {} Health {} FPS {}",
                    bounds("CPS"), bounds("Day"), bounds("Health"), bounds("FPS"));
                inGame = true;
                mc.setScreenAndShow(null);
                nextStep = System.nanoTime() + 1_000_000_000L;
            }
            case 13 -> {
                check(mc.gui.screen() == null, "the editor closed to gameplay");
                shot("hud-ingame-after-edit");
            }
            case 14 -> {
                inGame = false;
                mc.setScreenAndShow(screen);
                // Capture the organized defaults with every HUD previewed, restoring the fixture on close.
                button("reset");
                key(257, 0); // Enter answers the "Are you sure you want to reset?" question
                button("previews");
                for (Module module : modules.keySet()) module.getOptions().forEach(Option::reset);
                readyAt = System.nanoTime() + 500_000_000L;
                LoggerFactory.getLogger("TheLadsCore").info("Lads HUD editor probe END: {} passed, 0 failed; native mouse/key handlers through the preview mapping and actual GUI render bounds; fixture restored after capture", passed);
            }
            default -> throw new IllegalStateException("Unexpected HUD QA stage");
        }
    }

    /** The editor left on purpose for its in-game frame (NativeWorldVerification keeps the capture open). */
    boolean inGame() { return inGame; }
    boolean readyForCapture() { return stage >= STAGES && shot == null && System.nanoTime() >= readyAt; }

    /** A completed frame: once the requested state has drawn a few frames, it is saved as native-NAME-TIME.png. */
    void frame(com.mojang.blaze3d.pipeline.RenderTarget target, Path gameDirectory) {
        if (shot == null || shotTaken || ++shotFrames < 4) return;
        shotTaken = true;
        String name = shot;
        Path output = gameDirectory.resolve("screenshots").resolve("native-" + name + "-" + System.currentTimeMillis() + ".png");
        net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
            try { image.writeToFile(output); LoggerFactory.getLogger("TheLadsCore").info("Lads HUD editor capture {}: {}", name, output); }
            catch (Exception failure) { shotFailure = failure; }
            finally { image.close(); shot = null; shotTaken = false; }
        });
    }
    private void shot(String name) { shot = name; shotFrames = 0; }

    private void size(String name, double value) {
        var option = ModuleManager.getInstance().getModule(name).getOption("Size");
        check(option instanceof SliderOption, "native HUD scale option: " + name);
        ((SliderOption) option).setValue(value);
    }
    private Rect bounds(String name) {
        Rect result = controller.boundsFor(name);
        if (result == null) throw new IllegalStateException("Missing rendered HUD " + name);
        return result;
    }
    private void button(String id) {
        var control = controller.controls().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        check(control.enabled(), "button enabled: " + id);
        clickScreen(control.bounds());
    }
    /** A click at the centre of screen bounds: a control, a context item or a list row's switch. */
    private void clickScreen(Rect bounds) {
        var event = new MouseButtonEvent(bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false); screen.mouseReleased(event);
    }
    /** A click at the centre of a HUD's game bounds, through the preview. */
    private void click(Rect bounds, int modifiers) {
        var event = at(bounds, 0, 0, modifiers);
        screen.mouseClicked(event, false); screen.mouseReleased(event);
    }
    private MouseButtonEvent at(Rect bounds, int dx, int dy, int modifiers) {
        return new MouseButtonEvent(controller.screenX(bounds.x() + bounds.width() / 2.0 + dx), controller.screenY(bounds.y() + bounds.height() / 2.0 + dy),
            new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, nativeModifiers(modifiers)));
    }
    private void press(Rect bounds, int modifiers) { screen.mouseClicked(at(bounds, 0, 0, modifiers), false); }
    private void moveTo(Rect from, int dx, int dy) { screen.mouseDragged(at(from, dx, dy, 0), dx, dy); }
    private void releaseAt(Rect bounds, int modifiers) { screen.mouseReleased(at(bounds, 0, 0, modifiers)); }
    private void drag(Rect bounds, int dx, int dy, int modifiers) { press(bounds, modifiers); moveTo(bounds, dx, dy); screen.mouseReleased(at(bounds, dx, dy, modifiers)); }
    private static int nativeModifiers(int mods) {
        return ((mods & 1) != 0 ? InputConstants.MOD_SHIFT : 0) | ((mods & 2) != 0 ? InputConstants.MOD_CONTROL : 0);
    }
    private void key(int code, int modifiers) {
        int physical = switch (code) { case 71 -> InputConstants.KEY_G; case 257 -> InputConstants.KEY_RETURN; case 263 -> InputConstants.KEY_LEFT; case 264 -> InputConstants.KEY_DOWN; default -> throw new IllegalArgumentException("Unknown QA key"); };
        screen.keyPressed(new KeyEvent(physical, code == 71 ? 'g' : 0, nativeModifiers(modifiers)));
    }
    private boolean saved(String name, Rect bounds) {
        int[] position = settings.getPosition(name);
        return position != null && position[0] == bounds.x() && position[1] == bounds.y();
    }
    private static boolean relative(Rect a, Rect b, Rect originalA, Rect originalB) {
        return b.x() - a.x() == originalB.x() - originalA.x() && b.y() - a.y() == originalB.y() - originalA.y();
    }
    private void check(boolean result, String description) {
        if (!result) throw new IllegalStateException("HUD editor QA: " + description);
        passed++;
    }
    @Override public void close() {
        if (restored) return;
        restored = true;
        if (controller != null) controller.close();
        modules.forEach((module, state) -> {
            state.options().forEach(Option::load);
            module.setEnabled(state.enabled()); module.setLastModified(state.modified());
        });
        settings.getPositions().clear(); positions.forEach((name, value) -> settings.setPosition(name, value[0], value[1]));
        settings.replaceGroups(groups); settings.replaceLocked(locked);
        elementPositions.forEach((element, value) -> { element.endPositionEdit(); element.setPosition(value[0], value[1]); element.restoreSavedPosition(); });
    }
}
