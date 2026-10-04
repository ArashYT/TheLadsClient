package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.click;
import static com.thelads.core.v1_8_9.feature.CoreProbe.mouse;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;
import static com.thelads.core.v1_8_9.feature.CoreProbe.tap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.CpsTracker;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import com.thelads.core.v1_8_9.gui.DraggableHudScreen189;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

/**
 * QA only: the C2 checks, run by CoreProbe in its sandbox QA world with OptiFine loaded. Real frames: all 18 built-in HUD modules
 * draw live 1.8.9 data through RenderGameOverlayEvent.Post(ALL) (every text and item the adapter drew is recorded), the GL state
 * after the Lads HUD equals the state before it, and F1 hides it. Then the HUD editor through the real Edit HUD button, driven
 * with LWJGL input aimed through its preview: the list's gear, selection, centre snapping, a free drag, a box selection, the
 * right-click menu's Group, Center stack and Ungroup, a list switch with Show disabled, a plain drop that docks without
 * grouping, a Shift drop that groups, and Escape during a drag, each checked against the config file on disk.
 */
public final class HudProbe {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(HudProbe::setup, HudProbe::drawn, HudProbe::hidden,
        HudProbe::stillHidden, HudProbe::shown, HudProbe::cps, HudProbe::menu, HudProbe::editorOpened, HudProbe::editorShown,
        HudProbe::gearOpened, HudProbe::backInEditor, HudProbe::selected, HudProbe::centred, HudProbe::snapOff, HudProbe::freeDragged,
        HudProbe::multiSelected, HudProbe::contextMenu, HudProbe::grouped, HudProbe::resized, HudProbe::stackOffered, HudProbe::restacked,
        HudProbe::ungroupOffered, HudProbe::ungrouped, HudProbe::switchedOff, HudProbe::allShown, HudProbe::switchedOn, HudProbe::plainDragging,
        HudProbe::plainDocked, HudProbe::plainDropped, HudProbe::shiftDropped, HudProbe::shiftGrouped, HudProbe::shiftCaptured,
        HudProbe::dragging, HudProbe::escaped, HudProbe::back);
    private static final Set<String> PAIR = new HashSet<>(Arrays.asList("CPS", "Day"));
    private static final List<String> FIXTURE = Arrays.asList("CPS", "Day", "FPS", "Health");
    private static GlWatch watch;
    private static long frames, openedAt;
    private static int cpsBefore;
    private static ItemStack[] armor;
    private static ScoreObjective objective, sidebar;
    private static LadsSettingsScreen189 menu;
    private static DraggableHudScreen189 editor;
    private static Rect before;
    private static int[] expected;
    private static String layout;
    private HudProbe() {}

    /** Gameplay in the QA world: every built-in HUD module on with default options, no saved layout, real data for each. */
    private static boolean setup(Minecraft mc) throws Exception {
        if (mc.currentScreen != null || mc.thePlayer == null) return false;
        HudSettings.getInstance().clearPositions();
        HudSettings.getInstance().setTextShadow(true);
        HudSettings.getInstance().setBackgrounds(true);
        int on = 0;
        for (HudElement element : HudManager.getInstance().getElements()) {
            Module module = module(element.getModuleName());
            if (module == null) continue;
            module.setEnabled(ModuleSupport.isBuiltIn(module.getName()));
            if (!module.isEnabled()) continue;
            on++;
            for (Option option : module.getOptions()) option.reset();
        }
        // NativeHud's modules but Autohide (no HUD element), plus the elements of built-in gameplay modules (Toggle Sprint/Sneak).
        check(on >= NativeHud.MODULES.length - 1,
            "the " + on + " built-in HUD modules are switched on for the in-world check");
        // Client-side only (the integrated server never sees them): worn armour, a Speed effect and a sidebar objective.
        armor = mc.thePlayer.inventory.armorInventory.clone();
        mc.thePlayer.inventory.armorInventory[0] = new ItemStack(Items.iron_boots);
        mc.thePlayer.inventory.armorInventory[3] = new ItemStack(Items.diamond_helmet);
        mc.thePlayer.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 1200));
        Scoreboard board = mc.theWorld.getScoreboard();
        sidebar = board.getObjectiveInDisplaySlot(1);
        objective = board.addScoreObjective("lads_qa_c2", IScoreObjectiveCriteria.DUMMY);
        objective.setDisplayName("Lads QA");
        board.getValueFromObjective("QA", objective).setScorePoints(7);
        board.setObjectiveInDisplaySlot(1, objective);
        watch = new GlWatch();
        MinecraftForge.EVENT_BUS.register(watch);
        GuiLadsAdapter.recording = new ArrayList<>();
        frames = NativeHud.frames;
        return after(20);
    }

    private static boolean drawn(Minecraft mc) throws Exception {
        check(NativeHud.frames >= frames + 5, "the Lads HUD drew in " + (NativeHud.frames - frames) + " real frames through Forge's RenderGameOverlayEvent.Post(ALL)");
        List<String> drawn = new ArrayList<>(GuiLadsAdapter.recording);
        LadsGameBridge game = LadsGameBridge.get();
        String boots = new ItemStack(Items.iron_boots).getDisplayName(), helmet = new ItemStack(Items.diamond_helmet).getDisplayName();
        String[][] expected = {
            {"FPS", "\\d+ FPS"}, {"Coordinates", "X: " + game.getPlayerX()}, {"Coordinates", "Y: " + game.getPlayerY()},
            {"Coordinates", "Z: " + game.getPlayerZ()}, {"PingHUD", "Ping: \\d+ms"}, {"Memory", "\\d+/\\d+MB \\(\\d+%\\)"},
            {"Speed", "\\d+\\.\\d b/s"}, {"Day", "Day: " + game.getDayCount()}, {"Time", "\\d\\d:\\d\\d"}, {"XP", "Lvl " + game.getXpLevel()},
            {"Potion Effects", java.util.regex.Pattern.quote(I18n.format(Potion.moveSpeed.getName())) + " \\(\\d+s\\)"},
            {"CPS", "CPS: \\d+ \\| \\d+"}, {"Keystrokes", "LMB"}, {"Keystrokes", "RMB"}, {"Keystrokes", "\\d+ CPS"},
            {"Biome", "(?i)" + java.util.regex.Pattern.quote(game.getBiomeName())},
            {"Direction", game.getPlayerDirection().substring(0, 1).toUpperCase()},
            {"Health", (int) Math.ceil(game.getHealth()) + "/" + (int) Math.ceil(game.getMaxHealth())},
            {"Hunger", "Food: " + game.getFoodLevel() + "/20"}, {"TexturePacks", "Pack: .+"},
            {"ArmorHUD", "item:" + boots}, {"ArmorHUD", "item:" + helmet},
            {"ArmorHUD", boots + " " + new ItemStack(Items.iron_boots).getMaxDamage() + "/" + new ItemStack(Items.iron_boots).getMaxDamage()},
            {"ArmorHUD", helmet + " \\d+/\\d+"}, {"Scoreboard", "Lads QA"}, {"Scoreboard", "QA"}, {"Scoreboard", "7"}};
        List<String> missing = new ArrayList<>();
        Set<String> modules = new HashSet<>();
        for (String[] text : expected) {
            modules.add(text[0]);
            boolean found = false;
            for (String line : drawn) found |= line.matches(text[1]);
            if (!found) missing.add(text[0] + " '" + text[1] + "'");
        }
        // ponytail: the HUD modules added in 1.4.1 (Paperdoll, BossBar, Clock, ...) have no live-data expectation here yet.
        check(java.util.Arrays.asList(NativeHud.MODULES).containsAll(modules) && missing.isEmpty(), "each of the " + modules.size() + " built-in HUD modules drew its live 1.8.9 data "
            + "(texts, and armour items through RenderItem) " + missing + " of " + drawn.size() + " draws");
        check(!GuiIngameForge.renderObjective, "the Lads Scoreboard replaces vanilla's sidebar (GuiIngameForge.renderObjective off)");
        check(watch.frames > 0 && watch.mismatch == null, "GL state after the Lads HUD equals the state before it in " + watch.frames
            + " frames (enables, blend and alpha functions, depth mask, colour, matrix depth): " + (watch.mismatch == null ? watch.sample : watch.mismatch));
        screenshot(mc, "c2-hud-world");
        GuiLadsAdapter.recording.clear();
        tap(Keyboard.KEY_F1, '\0');
        return after(2);
    }

    private static boolean hidden(Minecraft mc) {
        check(mc.gameSettings.hideGUI, "F1 through runTick's keyboard loop hides the GUI");
        frames = NativeHud.frames;
        GuiLadsAdapter.recording.clear();
        return after(10);
    }

    private static boolean stillHidden(Minecraft mc) throws Exception {
        check(NativeHud.frames == frames && GuiLadsAdapter.recording.isEmpty(), "nothing of the Lads HUD draws while F1 hides the GUI");
        screenshot(mc, "c2-hud-f1-hidden");
        tap(Keyboard.KEY_F1, '\0');
        return after(10);
    }

    private static boolean shown(Minecraft mc) throws Exception {
        check(!mc.gameSettings.hideGUI && NativeHud.frames > frames && !GuiLadsAdapter.recording.isEmpty(), "F1 again brings the Lads HUD back");
        GuiLadsAdapter.recording = null;
        cpsBefore = CpsTracker.get().rightCps();
        for (int i = 0; i < 3; i++) {
            mouse(1, true, 0, 0);
            mouse(1, false, 0, 0);
        }
        return after(1);
    }

    private static boolean cps(Minecraft mc) throws Exception {
        check(CpsTracker.get().rightCps() == cpsBefore + 3, "three right clicks in one tick count 3 CPS (InputEvent.MouseInputEvent)");
        // The editor fixture of the 26.x probe: FPS clamped to the right edge, CPS, Day and Health on; the other built-in modules
        // are off, so the editor lists and previews only these four until Show disabled.
        for (String name : NativeHud.MODULES) module(name).setEnabled(FIXTURE.contains(name));
        ((SliderOption) module("Day").getOption("Size")).setValue(125);
        HudSettings.getInstance().setPosition("FPS", 10000, 10);
        HudSettings.getInstance().setPosition("CPS", 300, 60);
        HudSettings.getInstance().setPosition("Day", 12, 78);
        HudSettings.getInstance().setPosition("Health", 12, 112);
        tap(Keyboard.KEY_RSHIFT, '\0');
        return after(10);
    }

    private static boolean menu(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof LadsSettingsScreen189 && ((LadsSettingsScreen189) mc.currentScreen).parent() == null,
            "Right Shift opens the Lads menu over the QA world");
        menu = (LadsSettingsScreen189) mc.currentScreen;
        LadsSettingsScreen.Rect edit = menu.ui().controlBounds("hud");
        check(edit != null, "the Lads menu draws its Edit HUD button");
        click(edit.x() + edit.width() / 2, edit.y() + edit.height() / 2);
        return after(2);
    }

    private static boolean editorOpened(Minecraft mc) {
        check(mc.currentScreen instanceof DraggableHudScreen189 && ((DraggableHudScreen189) mc.currentScreen).parent() == menu,
            "a click on Edit HUD opens the HUD editor over the Lads menu");
        editor = (DraggableHudScreen189) mc.currentScreen;
        frames = NativeHud.frames;
        return after(10);
    }

    private static boolean editorShown(Minecraft mc) throws Exception {
        DraggableHudScreen ui = editor.ui();
        check(NativeHud.frames == frames, "the in-game Lads HUD pauses while the editor draws its previews");
        check(!GuiIngameForge.renderObjective, "vanilla's sidebar is hidden while editing");
        List<String> ids = ids(ui.controls());
        check(ids.containsAll(Arrays.asList("group", "ungroup", "lock", "snap", "done", "search", "previews", "colors", "reset")),
            "the editor toolbar and list controls are drawn " + ids);
        Rect preview = ui.previewBounds();
        boolean clear = true;
        for (DraggableHudScreen.Control control : ui.controls()) clear &= !control.bounds().intersects(preview);
        check(clear && Math.abs(preview.width() / (double) preview.height() - editor.width / (double) editor.height) < 0.02
            && preview.right() <= editor.width && preview.bottom() <= editor.height, "the framed preview keeps the game's aspect, beside the controls " + preview);
        check(ui.listedNames().equals(FIXTURE), "the list shows only the switched-on HUDs " + ui.listedNames());
        List<String> previews = new ArrayList<>();
        for (HudElement element : HudManager.getInstance().getElements())
            if (ui.boundsFor(element.getModuleName()) != null) previews.add(element.getModuleName());
        java.util.Collections.sort(previews, String.CASE_INSENSITIVE_ORDER);
        check(previews.equals(FIXTURE), "and previews only them " + previews);
        Rect cps = bounds("CPS"), fps = bounds("FPS");
        check(cps.x() == 300 && cps.y() == 60 && fps.right() == editor.width && fps.y() == 10, "saved positions place the previews, FPS clamped to the right edge");
        check(ui.toggleBoundsFor("CPS") != null && ui.settingsBoundsFor("CPS") != null, "CPS's list row has an ON/OFF switch and a settings gear");
        screenshot(mc, "c2-editor");
        openedAt = System.currentTimeMillis();
        clickScreen(ui.settingsBoundsFor("CPS"));
        return after(10);
    }

    private static boolean gearOpened(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof LadsSettingsScreen189 && ((LadsSettingsScreen189) mc.currentScreen).parent() == editor,
            "a click on CPS's gear opens the Lads menu over the editor");
        LadsSettingsScreen settings = ((LadsSettingsScreen189) mc.currentScreen).ui();
        check(settings.controlBounds("toggle:detail") != null && settings.controlBounds("option:Size") != null
            && module("CPS").getLastOpenedTime() >= openedAt, "at CPS's module settings");
        LadsSettingsScreen.Rect done = settings.controlBounds("close");
        click(done.x() + done.width() / 2, done.y() + done.height() / 2);
        return after(2);
    }

    private static boolean backInEditor(Minecraft mc) throws Exception {
        check(mc.currentScreen == editor, "Done returns from the module settings to the editor");
        clickGame(center(bounds("CPS")));
        return after(2);
    }

    private static boolean selected(Minecraft mc) throws Exception {
        check(editor.ui().selectedNames().equals(new HashSet<>(Arrays.asList("CPS"))), "a click in the preview selects CPS " + editor.ui().selectedNames());
        // Centre snapping: CPS's centre is dropped 2 px right of the screen centre, its top on a grid line.
        Rect cps = bounds("CPS");
        int[] from = center(cps);
        drag(from, editor.width / 2 + 2 - from[0], 40 - cps.y());
        return after(2);
    }

    private static boolean centred(Minecraft mc) throws Exception {
        Rect cps = bounds("CPS");
        check(cps.x() + cps.width() / 2 == editor.width / 2 && cps.y() == 40, "a drag in the preview (press, mouseClickMove, release) moved CPS and "
            + "centre snapping pulled it onto the screen centre " + cps);
        check(saved("CPS", cps), "the drag saved CPS's position to the config file");
        tap(Keyboard.KEY_G, 'g');
        return after(2);
    }

    private static boolean snapOff(Minecraft mc) throws Exception {
        check(control(editor.ui().controls(), "snap").label().equals("Snap: off"), "G turns snapping off");
        before = bounds("CPS");
        expected = drag(center(before), 13, 7);
        return after(2);
    }

    private static boolean freeDragged(Minecraft mc) throws Exception {
        Rect cps = bounds("CPS");
        check(cps.x() == before.x() + expected[0] && cps.y() == before.y() + expected[1] && saved("CPS", cps),
            "without snapping CPS moves by the exact pointer delta (" + expected[0] + ", " + expected[1] + " game pixels), saved " + cps);
        tap(Keyboard.KEY_G, 'g');
        // Box selection on the open preview: from above-left of Day to past CPS's centre, short of Health and FPS.
        int[] from = {2, 30};
        drag(from, editor.width / 2 + 60 - from[0], 100 - from[1]);
        return after(2);
    }

    private static boolean multiSelected(Minecraft mc) throws Exception {
        check(control(editor.ui().controls(), "snap").label().equals("Snap: on"), "G turns snapping back on");
        check(editor.ui().selectedNames().equals(PAIR), "a box drawn on the preview selects CPS and Day " + editor.ui().selectedNames());
        rightClick(bounds("CPS"));
        return after(2);
    }

    private static boolean contextMenu(Minecraft mc) throws Exception {
        List<DraggableHudScreen.Control> items = editor.ui().contextControls();
        check(ids(items).equals(Arrays.asList("settings", "lock", "centerX", "centerY", "centerBoth", "group", "ungroup", "stack", "toggle")),
            "a right click opens the context menu " + ids(items));
        check(editor.ui().selectedNames().equals(PAIR), "the right click keeps the two-HUD selection");
        check(control(items, "group").enabled() && !control(items, "ungroup").enabled() && !control(items, "stack").enabled(), "Group is offered for two ungrouped HUDs");
        screenshot(mc, "c2-editor-context-menu");
        clickScreen(control(items, "group").bounds());
        return after(2);
    }

    private static boolean grouped(Minecraft mc) throws Exception {
        check(PAIR.equals(HudSettings.getInstance().getGroupMembers("CPS")) && groupsOnDisk() == 1, "context Group creates the CPS + Day group and saves it");
        check(stacked(), "the new group is a centred stack " + bounds("CPS") + " / " + bounds("Day"));
        // A member that grows breaks the stack until Center stack re-stacks it.
        ((SliderOption) module("CPS").getOption("Size")).setValue(150);
        return after(2);
    }

    private static boolean resized(Minecraft mc) throws Exception {
        check(!stacked(), "CPS at 150% overlaps Day " + bounds("CPS") + " / " + bounds("Day") + ", CPS size "
            + ((SliderOption) module("CPS").getOption("Size")).getValue());
        rightClick(bounds("CPS"));
        return after(2);
    }

    private static boolean stackOffered(Minecraft mc) throws Exception {
        DraggableHudScreen.Control stack = control(editor.ui().contextControls(), "stack");
        check(stack != null && stack.enabled(), "Center stack is offered for the grouped selection");
        clickScreen(stack.bounds());
        return after(2);
    }

    private static boolean restacked(Minecraft mc) throws Exception {
        check(stacked() && PAIR.equals(HudSettings.getInstance().getGroupMembers("Day")), "context Center stack re-stacks the group");
        check(saved("CPS", bounds("CPS")) && saved("Day", bounds("Day")), "and saves both positions");
        rightClick(bounds("Day"));
        return after(2);
    }

    private static boolean ungroupOffered(Minecraft mc) throws Exception {
        DraggableHudScreen.Control ungroup = control(editor.ui().contextControls(), "ungroup");
        check(ungroup != null && ungroup.enabled(), "a right click on the other member offers Ungroup");
        clickScreen(ungroup.bounds());
        return after(2);
    }

    private static boolean ungrouped(Minecraft mc) throws Exception {
        check(HudSettings.getInstance().getGroupMembers("CPS") == null && HudSettings.getInstance().getGroupMembers("Day") == null && groupsOnDisk() == 0,
            "context Ungroup splits the group and saves it");
        Rect toggle = editor.ui().toggleBoundsFor("FPS");
        check(toggle != null, "FPS's list row has an ON/OFF switch");
        clickScreen(toggle);
        return after(2);
    }

    private static boolean switchedOff(Minecraft mc) throws Exception {
        check(!module("FPS").isEnabled() && !enabledOnDisk("FPS"), "FPS's list switch turns the module off and saves it");
        check(editor.ui().boundsFor("FPS") == null && !editor.ui().listedNames().contains("FPS"), "and it leaves the default list and preview");
        clickScreen(control(editor.ui().controls(), "previews").bounds());
        return after(2);
    }

    private static boolean allShown(Minecraft mc) throws Exception {
        check(editor.ui().isShowingAll() && editor.ui().boundsFor("FPS") != null && editor.ui().listedNames().contains("Memory"),
            "Show disabled lists and previews the switched-off HUDs " + editor.ui().listedNames());
        screenshot(mc, "c2-editor-all");
        clickScreen(editor.ui().toggleBoundsFor("FPS"));
        return after(2);
    }

    private static boolean switchedOn(Minecraft mc) throws Exception {
        check(module("FPS").isEnabled() && enabledOnDisk("FPS"), "and its switch turns FPS back on");
        clickScreen(control(editor.ui().controls(), "previews").bounds());
        return after(2);
    }

    /** Health dragged until it docks under Day, held there (no release) for the capture. */
    private static boolean plainDragging(Minecraft mc) throws Exception {
        check(!editor.ui().isShowingAll() && editor.ui().listedNames().equals(FIXTURE), "Show disabled off: the four switched-on HUDs again");
        Rect day = bounds("Day"), health = bounds("Health");
        int[] from = center(health);
        mouse(0, true, sx(from[0]), sy(from[1]));
        for (int i = 1; i <= 4; i++) mouse(-1, false, sx(from[0] + (day.x() + 1 - health.x()) * i / 4.0), sy(from[1] + (day.bottom() + 2 - health.y()) * i / 4.0));
        return after(2);
    }

    private static boolean plainDocked(Minecraft mc) throws Exception {
        Rect day = bounds("Day"), health = bounds("Health");
        check(editor.ui().isDragging() && health.y() == day.bottom() && health.x() == day.x(), "snapping docks Health under Day during the drag " + health + " / " + day);
        screenshot(mc, "c2-editor-dragging");
        mouse(0, false, sx(center(health)[0]), sy(center(health)[1]));
        return after(2);
    }

    private static boolean plainDropped(Minecraft mc) throws Exception {
        Rect day = bounds("Day"), health = bounds("Health");
        check(!editor.ui().isDragging() && HudSettings.getInstance().getGroupMembers("Health") == null && groupsOnDisk() == 0,
            "a plain drop docks Health without grouping it");
        check(health.y() == day.bottom() && saved("Health", health), "and saves it where the preview shows it " + health);
        screenshot(mc, "c2-editor-plain-drop");
        // The same drop with Shift: press and moves through LWJGL, the release with Shift through the editor (see shiftDropped).
        int[] from = center(health);
        mouse(0, true, sx(from[0]), sy(from[1]));
        mouse(-1, false, sx(from[0]), sy(from[1] + 30));
        mouse(-1, false, sx(from[0]), sy(from[1]));
        return after(2);
    }

    /**
     * Synthetic LWJGL key events cannot hold Shift (GuiScreen.isShiftKeyDown reads the device state, which Display.update
     * refreshes from the real keyboard), so this release goes to the editor with the Shift bit DraggableHudScreen189 passes
     * for a held Shift. The queued LWJGL release that follows is a no-op for the finished drag.
     */
    private static boolean shiftDropped(Minecraft mc) throws Exception {
        check(editor.ui().isDragging(), "the Shift drop's drag is in progress");
        int[] at = center(bounds("Health"));
        editor.ui().mouseReleased(editor.ui().screenX(at[0]), editor.ui().screenY(at[1]), 0, 1);
        mouse(0, false, sx(at[0]), sy(at[1]));
        return after(2);
    }

    private static boolean shiftGrouped(Minecraft mc) throws Exception {
        check(new HashSet<>(Arrays.asList("Day", "Health")).equals(HudSettings.getInstance().getGroupMembers("Health")) && groupsOnDisk() == 1,
            "a Shift drop groups Health with Day, which it docks under, and saves it " + HudSettings.getInstance().getGroupMembers("Health"));
        clickGame(center(bounds("Health")));
        // Snapping off for the last drag, so its exact delta shows. A step ahead of that drag: GuiScreen handles the queued mouse
        // events before the queued keys, and the snap toggle finishes any drag.
        tap(Keyboard.KEY_G, 'g');
        return after(2);
    }

    private static boolean shiftCaptured(Minecraft mc) throws Exception {
        screenshot(mc, "c2-editor-shift-group");
        check(control(editor.ui().controls(), "snap").label().equals("Snap: off"), "snapping is off for the unfinished drag");
        // Escape during a drag: pressed and moved, never released.
        before = bounds("Day");
        int[] from = center(before);
        mouse(0, true, sx(from[0]), sy(from[1]));
        mouse(-1, false, sx(from[0] + 25), sy(from[1]));
        expected = new int[] {(int) Math.round((sx(from[0] + 25) - sx(from[0])) / editor.ui().previewScale()), 0};
        return after(2);
    }

    private static boolean dragging(Minecraft mc) throws Exception {
        check(editor.ui().isDragging() && bounds("Day").x() == before.x() + expected[0] && saved("Day", before),
            "Day follows an unfinished drag, not saved yet (from " + before + " to " + bounds("Day") + ", dragging " + editor.ui().isDragging() + ")");
        tap(Keyboard.KEY_ESCAPE, (char) 27);
        return after(2);
    }

    private static boolean escaped(Minecraft mc) throws Exception {
        check(mc.currentScreen == menu, "Escape closes the editor back to the Lads menu");
        HudElement day = element("Day");
        check(day.getX() != before.x() && saved("Day", new Rect(day.getX(), day.getY(), 0, 0)),
            "Escape finished the drag and saved Day where it was dropped (" + day.getX() + ", " + day.getY() + ")");
        int[] cps = HudSettings.getInstance().getPosition("CPS");
        check(cps != null && saved("CPS", new Rect(cps[0], cps[1], 0, 0)) && enabledOnDisk("CPS") && !enabledOnDisk("Scoreboard"),
            "the config file holds the edited layout and switches");
        layout = "CPS " + positionOf("CPS") + ", Day " + positionOf("Day") + ", Health " + positionOf("Health") + ", FPS " + positionOf("FPS");
        frames = NativeHud.frames;
        tap(Keyboard.KEY_RSHIFT, '\0');
        return after(10);
    }

    private static boolean back(Minecraft mc) {
        check(mc.currentScreen == null && NativeHud.frames > frames, "the Lads HUD draws again in gameplay");
        // The edited layout in game, to compare with the editor captures: GUI positions x2 (GUI scale 2) in pixels.
        screenshot(mc, "c2-hud-after-edit");
        org.apache.logging.log4j.LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 HUD layout after editing (GUI coordinates): {}", layout);
        check(GuiIngameForge.renderObjective, "vanilla's sidebar returns with the Lads Scoreboard off");
        check(watch.mismatch == null, "GL state around the Lads HUD stayed unchanged in all " + watch.frames + " frames");
        Scoreboard board = mc.theWorld.getScoreboard();
        board.setObjectiveInDisplaySlot(1, sidebar);
        board.removeObjective(objective);
        System.arraycopy(armor, 0, mc.thePlayer.inventory.armorInventory, 0, armor.length);
        mc.thePlayer.removePotionEffectClient(Potion.moveSpeed.id);
        stop();
        return true;
    }

    /** Ends the recording (also when the probe fails). */
    static void stop() {
        GuiLadsAdapter.recording = null;
        if (watch != null) MinecraftForge.EVENT_BUS.unregister(watch);
    }

    /** GUI pixel of a game GUI position inside the editor's preview. */
    private static int sx(double gameX) { return (int) Math.round(editor.ui().screenX(gameX)); }
    private static int sy(double gameY) { return (int) Math.round(editor.ui().screenY(gameY)); }

    private static void clickGame(int[] game) throws Exception {
        click(sx(game[0]), sy(game[1]));
    }

    private static void clickScreen(Rect bounds) throws Exception {
        click(bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2);
    }

    /**
     * A drag in game pixels through the preview, in one tick (press, four mouseClickMove steps, release), so a real pointer
     * cannot interleave. Returns the delta in game pixels that the whole GUI pixels sent amount to.
     */
    private static int[] drag(int[] from, double dx, double dy) throws Exception {
        mouse(0, true, sx(from[0]), sy(from[1]));
        for (int i = 1; i <= 4; i++) mouse(-1, false, sx(from[0] + dx * i / 4), sy(from[1] + dy * i / 4));
        mouse(0, false, sx(from[0] + dx), sy(from[1] + dy));
        double scale = editor.ui().previewScale();
        return new int[] {(int) Math.round((sx(from[0] + dx) - sx(from[0])) / scale), (int) Math.round((sy(from[1] + dy) - sy(from[1])) / scale)};
    }

    private static void rightClick(Rect bounds) throws Exception {
        mouse(1, true, sx(bounds.x() + 2), sy(bounds.y() + 2));
        mouse(1, false, sx(bounds.x() + 2), sy(bounds.y() + 2));
    }

    private static String positionOf(String name) {
        int[] position = HudSettings.getInstance().getPosition(name);
        return position == null ? "default" : position[0] + "," + position[1];
    }

    /** CPS directly above Day, centres aligned (one width), 2 px apart, as the editor stacks a group. */
    private static boolean stacked() {
        Rect cps = bounds("CPS"), day = bounds("Day");
        return Math.abs(cps.x() + cps.width() / 2 - (day.x() + day.width() / 2)) <= 1 && day.y() == cps.bottom() + 2;
    }

    private static Rect bounds(String name) {
        Rect bounds = editor.ui().boundsFor(name);
        if (bounds == null) throw new IllegalStateException("1.8.9 HUD QA: no rendered " + name + " preview");
        return bounds;
    }

    private static int[] center(Rect bounds) {
        return new int[] {bounds.x() + bounds.width() / 2, bounds.y() + bounds.height() / 2};
    }

    private static DraggableHudScreen.Control control(List<DraggableHudScreen.Control> controls, String id) {
        for (DraggableHudScreen.Control control : controls) if (control.id().equals(id)) return control;
        return null;
    }

    private static List<String> ids(List<DraggableHudScreen.Control> controls) {
        List<String> ids = new ArrayList<>();
        for (DraggableHudScreen.Control control : controls) ids.add(control.id());
        return ids;
    }

    private static HudElement element(String name) {
        for (HudElement element : HudManager.getInstance().getElements()) if (name.equals(element.getModuleName())) return element;
        throw new IllegalStateException("1.8.9 HUD QA: no " + name + " element");
    }

    private static Module module(String name) {
        return ModuleManager.getInstance().getModule(name);
    }

    /** The position is in HudSettings and in the config file (thelads_config.json in this profile's THELADS_DIR). */
    private static boolean saved(String name, Rect bounds) throws Exception {
        int[] live = HudSettings.getInstance().getPosition(name);
        JsonArray disk = config().getAsJsonObject("hud").getAsJsonObject("positions").getAsJsonArray(name);
        return live != null && live[0] == bounds.x() && live[1] == bounds.y()
            && disk != null && disk.get(0).getAsInt() == bounds.x() && disk.get(1).getAsInt() == bounds.y();
    }

    private static boolean enabledOnDisk(String name) throws Exception {
        return config().getAsJsonObject("modules").getAsJsonObject(name).get("enabled").getAsBoolean();
    }

    private static int groupsOnDisk() throws Exception {
        return config().getAsJsonObject("hud").getAsJsonArray("groups").size();
    }

    private static JsonObject config() throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(ClientPaths.getConfigFile().toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** GL state around the Lads HUD: Forge calls the highest-priority Post(ALL) listener first and the lowest last. */
    public static final class GlWatch {
        int frames;
        String mismatch, sample;
        private String before;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void before(RenderGameOverlayEvent.Post event) {
            if (event.type == RenderGameOverlayEvent.ElementType.ALL) sample = before = state();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void after(RenderGameOverlayEvent.Post event) {
            if (event.type != RenderGameOverlayEvent.ElementType.ALL || before == null) return;
            String after = state();
            frames++;
            if (mismatch == null && !after.equals(before)) mismatch = before + " -> " + after;
            before = null;
        }

        private static String state() {
            StringBuilder state = new StringBuilder("enabled ");
            for (int capability : new int[] {GL11.GL_BLEND, GL11.GL_ALPHA_TEST, GL11.GL_DEPTH_TEST, GL11.GL_LIGHTING, GL11.GL_LIGHT0, GL11.GL_LIGHT1,
                GL11.GL_COLOR_MATERIAL, GL11.GL_TEXTURE_2D, GL12.GL_RESCALE_NORMAL, GL11.GL_CULL_FACE, GL11.GL_SCISSOR_TEST})
                state.append(GL11.glIsEnabled(capability) ? '1' : '0');
            FloatBuffer colour = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, colour);
            return state.append(" blend ").append(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)).append('/').append(GL11.glGetInteger(GL14.GL_BLEND_DST_RGB))
                .append('/').append(GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)).append('/').append(GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA))
                .append(" alpha ").append(GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC)).append('/').append(GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF))
                .append(" depth ").append(GL11.glGetInteger(GL11.GL_DEPTH_FUNC)).append('/').append(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK))
                .append(" shade ").append(GL11.glGetInteger(GL11.GL_SHADE_MODEL)).append(" matrix ").append(GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH))
                .append(" colour ").append(colour.get(0)).append(',').append(colour.get(1)).append(',').append(colour.get(2)).append(',').append(colour.get(3))
                .toString();
        }
    }
}
