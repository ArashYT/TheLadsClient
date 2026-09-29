package com.thelads.core.v26_2.feature;

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

/** Explicit isolated-world QA. Real native events and rendered bounds; fixture changes never save to disk. */
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
    private boolean restored;
    private Rect beforeCps, beforeDay;
    private static final Set<String> PAIR = Set.of("CPS", "Day");

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
                boolean fixture = Set.of("FPS", "CPS", "Day", "Health").contains(module.getName());
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
        if (stage >= 5 || System.nanoTime() < nextStep) return;
        if (controller.boundsFor("CPS") == null) return;
        nextStep = System.nanoTime() + 300_000_000L;
        switch (stage++) {
            case 0 -> {
                var mc = Minecraft.getInstance();
                Rect cps = bounds("CPS"), fps = bounds("FPS");
                check(cps.width() >= Math.ceil((mc.font.width("CPS: 0 | 0") + 12) * 1.5), "first-frame scaled CPS text fits its measured bounds");
                check(fps.right() <= screen.width && fps.x() >= 0, "first-frame FPS edge clamp uses its measured width");
                check(controller.controls().stream().map(DraggableHudScreen.Control::id).toList()
                    .containsAll(List.of("group", "ungroup", "lock", "unlock", "snap", "done")), "visible editing controls");
                click(cps, 0); click(bounds("Day"), 2);
                check(controller.selectedNames().equals(PAIR), "native Ctrl-click selects two HUDs");
                key(71, 2);
                check(PAIR.equals(settings.getGroupMembers("CPS")), "native Ctrl-G creates a persistent group");
            }
            case 1 -> {
                beforeCps = bounds("CPS"); beforeDay = bounds("Day");
                drag(beforeCps, 10000, -10000);
                check(relative(bounds("CPS"), bounds("Day"), beforeCps, beforeDay), "group border drag preserves both offsets");
            }
            case 2 -> {
                Rect cps = bounds("CPS"), day = bounds("Day");
                check(relative(cps, day, beforeCps, beforeDay), "next completed render preserves grouped offsets");
                check(cps.x() >= 0 && day.x() >= 0 && Math.max(cps.right(), day.right()) == screen.width
                    && Math.min(cps.y(), day.y()) == 0, "whole group clamps to viewport edges");
                check(saved("CPS", cps) && saved("Day", day), "all grouped positions match rendered coordinates after save");
                button("lock");
                check(settings.isLocked("CPS") && settings.isLocked("Day"), "Lock position applies to the group");
                beforeCps = cps; beforeDay = day;
                drag(cps, -80, 30); key(263, 0);
                check(bounds("CPS").equals(cps) && bounds("Day").equals(day), "locked group rejects mouse drag and arrow movement");
                // Clear selection on open canvas, then select the locked group again.
                click(new Rect(screen.width / 2, screen.height / 2, 1, 1), 0);
                click(cps, 0);
                check(controller.selectedNames().equals(PAIR), "locked group remains selectable for unlocking");
            }
            case 3 -> {
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
            }
            case 4 -> {
                button("group");
                check(PAIR.equals(settings.getGroupMembers("Day")), "visible Group button also creates the group");
                key(71, 0); // Disable snapping for an exact final fixture position.
                Rect cps = bounds("CPS");
                drag(cps, 44 - cps.x(), 58 - cps.y());
                check(bounds("CPS").x() == 44 && bounds("CPS").y() == 58, "free drag keeps pointer and saved GUI coordinates aligned");
                check(saved("CPS", bounds("CPS")) && saved("Day", bounds("Day")), "final group positions are persisted together");
                check(saves >= 7, "edit operations invoke the persistence owner");
                readyAt = System.nanoTime() + 500_000_000L;
                LoggerFactory.getLogger("TheLadsCore").info("Lads HUD editor probe END: {} passed, 0 failed; native mouse/key handlers and actual GUI render bounds; fixture restored after capture", passed);
            }
            default -> throw new IllegalStateException("Unexpected HUD QA stage");
        }
    }

    boolean readyForCapture() { return stage >= 5 && System.nanoTime() >= readyAt; }
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
        click(control.bounds(), 0);
    }
    private void click(Rect bounds, int modifiers) {
        double x = bounds.x() + Math.max(0.5, bounds.width() / 2.0), y = bounds.y() + Math.max(0.5, bounds.height() / 2.0);
        var event = new MouseButtonEvent(x, y, new MouseButtonInfo(0, modifiers));
        screen.mouseClicked(event, false); screen.mouseReleased(event);
    }
    private void drag(Rect bounds, int dx, int dy) {
        double x = bounds.x() + bounds.width() / 2.0, y = bounds.y() + bounds.height() / 2.0;
        screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
        var event = new MouseButtonEvent(x + dx, y + dy, new MouseButtonInfo(0, 0));
        screen.mouseDragged(event, dx, dy); screen.mouseReleased(event);
    }
    private void key(int code, int modifiers) { screen.keyPressed(new KeyEvent(code, 0, modifiers)); }
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
