package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.win32.StdCallLibrary;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.modules.ZoomModule;
import com.thelads.core.v1_8_9.gui.ControlsScreen189;
import com.thelads.core.v1_8_9.mixin.EntityRendererAccessor;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * QA only (LADS_VERIFY_189_ONLY=keybinds): side mouse buttons, and key binds that last. Side buttons are WM_XBUTTONDOWN/UP
 * messages the game posts to its own window (no OS input, no focus: the QA window stays minimized), so they take a real press's
 * path: the thread's message queue, SideButtons189's hook, LWJGL's window procedure and event queue, then Minecraft. Keys and
 * clicks go into LWJGL's queues (CoreProbe). The phase is the first word of &lt;game&gt;/.lads-qa-keybinds:
 * <ul>
 * <li>bind: without SideButtons189's hook (LWJGL as shipped) Mouse 4 pressed with Shift and the left button held arrives as
 * Mouse 5 and leaves Mouse 5 down; with it, as Mouse 4. Reset All is saved at once. Controls binds Drop to Mouse 4 and Lads Zoom to
 * Mouse 5 through the side buttons, Jump, Toggle Sprint, OptiFine's zoom and an Essential key (when loaded) to keys, each in
 * options.txt at once; the names show as Button 4/5. In the QA world the side buttons, with keys and buttons held, drop an item,
 * zoom, toggle sprint, press the Essential key and (Lads Zoom off) zoom OptiFine. lads-qa/172-keybinds-expected.txt gets the binds.</li>
 * <li>check: after a restart every expected bind is live and in options.txt, and the side buttons drop and zoom.</li>
 * <li>hold: check, then Open Chat to Y (saved at once); the game then waits in its world to be killed.</li>
 * <li>storm: on the title screen, options saved back to back, and the same lines written as vanilla wrote them to
 * lads-qa/options-plain.txt, until the game is killed.</li>
 * </ul>
 */
final class Probe172Keybinds {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int WM_XBUTTONDOWN = 0x020B, WM_XBUTTONUP = 0x020C;
    private static final int MK_LBUTTON = 0x1, MK_RBUTTON = 0x2, MK_SHIFT = 0x4, MK_CONTROL = 0x8, MK_XBUTTON1 = 0x20, MK_XBUTTON2 = 0x40;
    private static final int MOUSE4 = -97, MOUSE5 = -96;
    static final String PHASE = phase();
    private static final Events EVENTS = new Events();
    private static ControlsScreen189 controls;
    private static KeyBinding essential, optiFine;
    private static boolean focusWas, started, toggledWas, realRan, zoomWas, sprintWas;
    private static int heldBefore;
    private static float baseFov;
    private static long saves;

    private Probe172Keybinds() {}

    static List<CoreProbe.Step> steps() {
        List<CoreProbe.Step> steps = new ArrayList<>();
        if ("storm".equals(PHASE)) {
            steps.add(Probe172Keybinds::storm);
            return steps;
        }
        steps.add(Probe172Keybinds::start);
        if ("bind".equals(PHASE)) {
            steps.addAll(Arrays.<CoreProbe.Step>asList(Probe172Keybinds::shipped, Probe172Keybinds::shippedSeen, Probe172Keybinds::hooked,
                Probe172Keybinds::hookedSeen, mc -> real(mc, false), mc -> realSeen(mc, false), mc -> real(mc, true), mc -> realSeen(mc, true), Probe172Keybinds::openControls, Probe172Keybinds::reset, Probe172Keybinds::pressReset, Probe172Keybinds::confirmReset, Probe172Keybinds::resetSaved,
                Probe172Keybinds::bindDrop, Probe172Keybinds::bindZoom, Probe172Keybinds::bindZoomed,
                mc -> bindKey(mc, mc.gameSettings.keyBindJump, Keyboard.KEY_V, 'v'), mc -> bound(mc, mc.gameSettings.keyBindJump, Keyboard.KEY_V),
                mc -> bindKey(mc, Toggles189.TOGGLE_SPRINT, Keyboard.KEY_J, 'j'), mc -> bound(mc, Toggles189.TOGGLE_SPRINT, Keyboard.KEY_J),
                mc -> optiFine == null ? after(1) : bindKey(mc, optiFine, Keyboard.KEY_N, 'n'),
                mc -> optiFine == null ? after(1) : bound(mc, optiFine, Keyboard.KEY_N),
                mc -> essential == null ? after(1) : bindKey(mc, essential, Keyboard.KEY_K, 'k'),
                mc -> essential == null ? after(1) : bound(mc, essential, Keyboard.KEY_K),
                Probe172Keybinds::names, Probe172Keybinds::namesShown, Probe172Keybinds::inWorld));
        } else {
            steps.add(Probe172Keybinds::expectedLive);
            steps.add(Probe172Keybinds::inWorld);
        }
        steps.addAll(Arrays.<CoreProbe.Step>asList(Probe172Keybinds::drop, Probe172Keybinds::dropped, Probe172Keybinds::dropUp,
            Probe172Keybinds::zoom, Probe172Keybinds::zoomed, Probe172Keybinds::zoomUp));
        if ("bind".equals(PHASE)) steps.addAll(Arrays.<CoreProbe.Step>asList(Probe172Keybinds::toggle, Probe172Keybinds::toggled,
            Probe172Keybinds::essentialDown, Probe172Keybinds::essentialUp, Probe172Keybinds::essentialReleased, Probe172Keybinds::optiFineDown, Probe172Keybinds::optiFineUp,
            Probe172Keybinds::writeExpected));
        if ("hold".equals(PHASE)) steps.addAll(Arrays.<CoreProbe.Step>asList(Probe172Keybinds::openControls,
            mc -> bindKey(mc, mc.gameSettings.keyBindChat, Keyboard.KEY_Y, 'y'), mc -> bound(mc, mc.gameSettings.keyBindChat, Keyboard.KEY_Y),
            Probe172Keybinds::writeExpected, Probe172Keybinds::waitForKill));
        steps.add(Probe172Keybinds::finish);
        return steps;
    }

    private static String phase() {
        try {
            File file = new File(Minecraft.getMinecraft().mcDataDir, ".lads-qa-keybinds");
            return file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim().split("\\s+")[0] : "bind";
        } catch (Exception e) {
            return "bind";
        }
    }

    /** Mouse events as Minecraft reads them (gameplay: runTick's input loop; a screen: GuiScreen.handleInput). */
    static final class Events {
        final List<String> seen = new ArrayList<>();

        @SubscribeEvent
        public void gameplay(InputEvent.MouseInputEvent event) { add(); }

        @SubscribeEvent
        public void screen(GuiScreenEvent.MouseInputEvent.Pre event) { add(); }

        private void add() {
            if (Mouse.getEventButton() >= 0) seen.add((Mouse.getEventButtonState() ? "Mouse " : "release Mouse ") + (Mouse.getEventButton() + 1));
        }
    }

    /** A side button as Windows sends it: 1 is Mouse 4, 2 Mouse 5; held: the keys and buttons held meanwhile (MK_ flags). */
    private static void side(int button, boolean down, int held) throws Exception {
        long flags = held | (down ? button == 1 ? MK_XBUTTON1 : MK_XBUTTON2 : 0);
        User32.INSTANCE.PostMessage(Borderless189.hwnd(), down ? WM_XBUTTONDOWN : WM_XBUTTONUP, new WPARAM((long) button << 16 | flags), new LPARAM(0));
    }

    private static File options(Minecraft mc) { return new File(mc.mcDataDir, "options.txt"); }

    /** options.txt as on disk: key -> value (the last line of a key). */
    private static Map<String, String> saved(Minecraft mc) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : Files.readAllLines(options(mc).toPath(), Charset.defaultCharset())) {
            int colon = line.indexOf(':');
            if (colon > 0) map.put(line.substring(0, colon), line.substring(colon + 1));
        }
        return map;
    }

    private static boolean onDisk(Minecraft mc, KeyBinding key, int code) throws Exception {
        return String.valueOf(code).equals(saved(mc).get("key_" + key.getKeyDescription())) && !new File(options(mc).getPath() + ".tmp").exists();
    }

    private static KeyBinding byName(Minecraft mc, String description) {
        for (KeyBinding key : mc.gameSettings.keyBindings) if (key.getKeyDescription().equals(description)) return key;
        return null;
    }

    /**
     * LWJGL takes the mouse capture (SetCapture) at a button press and releases it at the release. Held at a button that does
     * not exist while the probe runs, so its messages change nothing outside the game; -1 (none) afterwards.
     */
    private static void capture(int button) throws ReflectiveOperationException {
        Field impl = Display.class.getDeclaredField("display_impl");
        impl.setAccessible(true);
        Object display = impl.get(null);
        Field capture = display.getClass().getDeclaredField("captureMouse");
        capture.setAccessible(true);
        capture.setInt(display, button);
    }

    private static boolean start(Minecraft mc) throws Exception {
        capture(99);
        if (!started) MinecraftForge.EVENT_BUS.register(EVENTS);
        started = true;
        optiFine = byName(mc, "of.key.zoom");
        List<String> essentials = new ArrayList<>();
        for (KeyBinding key : mc.gameSettings.keyBindings) {
            String category = key.getKeyCategory().toLowerCase(Locale.ROOT);
            if (!category.contains("essential")) continue;
            essentials.add(key.getKeyDescription() + "=" + key.getKeyCode());
            // Chat Peek: a hold key that opens nothing (Essential's other keys open its screens).
            if ("Chat Peek".equals(key.getKeyDescription())) essential = key;
        }
        LOG.info("Lads 1.8.9 keybinds probe: phase {}; OptiFine zoom {}; Essential keys {}; SideButtons189 hook {}", PHASE,
            optiFine == null ? "absent" : optiFine.getKeyCode(), essentials, SideButtons189.hook != null);
        check(SideButtons189.hook != null, "Keybinds: SideButtons189's message hook is on the client thread");
        Zoom189.synthetic = true; // the QA window is not focused
        zoomWas = Zoom189.zoom().isEnabled();
        Zoom189.zoom().setEnabled(true); // Lads Zoom on, as by default
        return after(5);
    }

    /** LWJGL as shipped: Mouse 4 with Shift and the left button held. */
    private static boolean shipped(Minecraft mc) throws Exception {
        SideButtons189.remove();
        EVENTS.seen.clear();
        side(1, true, MK_SHIFT | MK_LBUTTON);
        side(1, false, MK_SHIFT | MK_LBUTTON);
        return after(3);
    }

    private static boolean shippedSeen(Minecraft mc) throws Exception {
        boolean stuck = Mouse.isButtonDown(4);
        check(EVENTS.seen.equals(Arrays.asList("Mouse 5", "release Mouse 4")) && stuck,
            "Keybinds: LWJGL as shipped turns Mouse 4 (Shift and left button held) into Mouse 5, and Mouse 5 stays down " + EVENTS.seen);
        side(2, false, 0); // Mouse 5's release, so the next steps start clean
        SideButtons189.install();
        return after(3);
    }

    private static boolean hooked(Minecraft mc) throws Exception {
        check(SideButtons189.hook != null && !Mouse.isButtonDown(4), "Keybinds: the hook is back and Mouse 5 is up");
        EVENTS.seen.clear();
        side(1, true, MK_SHIFT | MK_LBUTTON);
        side(1, false, MK_SHIFT | MK_LBUTTON);
        side(1, true, MK_CONTROL | MK_RBUTTON);
        side(2, true, MK_CONTROL | MK_RBUTTON | MK_XBUTTON1); // Mouse 5 while Mouse 4 is still held
        side(2, false, MK_CONTROL | MK_RBUTTON | MK_XBUTTON1);
        side(1, false, MK_CONTROL | MK_RBUTTON);
        side(2, true, 0);
        side(2, false, 0);
        return after(3);
    }

    private static boolean hookedSeen(Minecraft mc) {
        check(EVENTS.seen.equals(Arrays.asList("Mouse 4", "release Mouse 4", "Mouse 4", "Mouse 5", "release Mouse 5", "release Mouse 4",
            "Mouse 5", "release Mouse 5")) && !Mouse.isButtonDown(3) && !Mouse.isButtonDown(4),
            "Keybinds: with the hook each side button arrives as itself, with Shift, Ctrl and the other buttons held " + EVENTS.seen);
        return after(2);
    }

    /** user32's legacy input calls: real OS input, as a mouse and keyboard make it. */
    public interface Input extends StdCallLibrary {
        void keybd_event(byte key, byte scan, int flags, Pointer extra);
        void mouse_event(int flags, int dx, int dy, int data, Pointer extra);
    }

    /**
     * Only when the run asks for it (LADS_VERIFY_FOCUS=1, a run that may take focus: the exclusive QA lock, nobody at the PC):
     * Mouse 4 pressed with Shift held through Windows' own input (keybd_event and mouse_event), so Windows writes the message and
     * its flags itself; first without the hook, then with it. And only while the QA window is focused and holds the mouse (the
     * cursor is on it): elsewhere the side button would go to another program.
     */
    private static boolean realInput() {
        return "1".equals(System.getenv("LADS_VERIFY_FOCUS")) && Display.isActive() && Mouse.isGrabbed();
    }

    private static boolean real(Minecraft mc, boolean hooked) {
        realRan = realInput();
        if (!realRan) {
            if (!hooked) LOG.info("Lads 1.8.9 keybinds probe: real OS input skipped (needs LADS_VERIFY_FOCUS=1 and the QA window focused)");
            return after(1);
        }
        if (!hooked) SideButtons189.remove();
        EVENTS.seen.clear();
        Input input = (Input) Native.loadLibrary("user32", Input.class);
        input.keybd_event((byte) 0x10, (byte) 0, 0, null); // Shift down
        try {
            input.mouse_event(0x80, 0, 0, 1, null); // XBUTTON1 down
            input.mouse_event(0x100, 0, 0, 1, null); // XBUTTON1 up
        } finally {
            input.keybd_event((byte) 0x10, (byte) 0, 2, null); // Shift up
        }
        return after(5);
    }

    private static boolean realSeen(Minecraft mc, boolean hooked) throws Exception {
        if (!realRan) return after(1);
        List<String> seen = new ArrayList<>(EVENTS.seen);
        if (!hooked) {
            boolean stuck = Mouse.isButtonDown(4);
            side(2, false, 0);
            SideButtons189.install();
            check(seen.equals(Arrays.asList("Mouse 5", "release Mouse 4")) && stuck,
                "Keybinds: real input, LWJGL as shipped: Mouse 4 with Shift held arrives as Mouse 5, which stays down " + seen);
        } else check(seen.equals(Arrays.asList("Mouse 4", "release Mouse 4")) && !Mouse.isButtonDown(3) && !Mouse.isButtonDown(4),
            "Keybinds: real input with the hook: Mouse 4 with Shift held arrives as Mouse 4 " + seen);
        return after(3);
    }

    private static boolean openControls(Minecraft mc) {
        mc.displayGuiScreen(new GuiControls(null, mc.gameSettings));
        return after(10);
    }

    private static boolean reset(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof ControlsScreen189, "Keybinds: Controls is the Lads Controls screen");
        controls = (ControlsScreen189) mc.currentScreen;
        // Something off its default, so Reset All has work to do.
        mc.gameSettings.keyBindDrop.setKeyCode(Keyboard.KEY_B);
        KeyBinding.resetKeyBindingArrayAndHash();
        return after(3); // Controls enables Reset All when it draws a key off its default
    }

    private static boolean pressReset(Minecraft mc) throws Exception {
        press(I18n.format("controls.resetAll"));
        return after(5);
    }

    private static boolean confirmReset(Minecraft mc) throws Exception {
        press("Confirm?");
        return after(5);
    }

    private static boolean resetSaved(Minecraft mc) throws Exception {
        boolean defaults = true;
        for (KeyBinding key : mc.gameSettings.keyBindings) defaults &= key.getKeyCode() == key.getKeyCodeDefault();
        check(defaults && onDisk(mc, mc.gameSettings.keyBindDrop, Keyboard.KEY_Q), "Keybinds: Reset All puts every bind back and options.txt has it at once");
        return after(2);
    }

    /** Drop to Mouse 4 the way a player does it: its row's button, then the side button (Shift and left button held). */
    private static boolean bindDrop(Minecraft mc) throws Exception {
        controls.buttonId = mc.gameSettings.keyBindDrop;
        side(1, true, MK_SHIFT | MK_LBUTTON);
        side(1, false, MK_SHIFT | MK_LBUTTON);
        return after(3);
    }

    private static boolean bindZoom(Minecraft mc) throws Exception {
        check(controls.buttonId == null && mc.gameSettings.keyBindDrop.getKeyCode() == MOUSE4 && onDisk(mc, mc.gameSettings.keyBindDrop, MOUSE4),
            "Keybinds: Controls binds Drop to Mouse 4 (" + mc.gameSettings.keyBindDrop.getKeyCode() + ") and options.txt has it at once");
        controls.buttonId = Zoom189.ZOOM;
        side(2, true, MK_RBUTTON);
        side(2, false, MK_RBUTTON);
        return after(3);
    }

    private static boolean bindZoomed(Minecraft mc) throws Exception {
        check(Zoom189.ZOOM.getKeyCode() == MOUSE5 && onDisk(mc, Zoom189.ZOOM, MOUSE5),
            "Keybinds: Controls binds Lads Zoom to Mouse 5 (" + Zoom189.ZOOM.getKeyCode() + ") and options.txt has it at once");
        return after(1);
    }

    private static boolean bindKey(Minecraft mc, KeyBinding key, int code, char typed) throws Exception {
        check(mc.currentScreen instanceof ControlsScreen189, "Keybinds: Controls is open");
        controls = (ControlsScreen189) mc.currentScreen;
        controls.buttonId = key;
        CoreProbe.tap(code, typed);
        return after(3);
    }

    private static boolean bound(Minecraft mc, KeyBinding key, int code) throws Exception {
        check(key.getKeyCode() == code && onDisk(mc, key, code), "Keybinds: Controls binds " + key.getKeyDescription() + " to "
            + GameSettings.getKeyDisplayString(code) + " and options.txt has it at once");
        return after(1);
    }

    /** The side buttons' names, and the search finding binds by them. */
    private static boolean names(Minecraft mc) throws Exception {
        check("Button 4".equals(GameSettings.getKeyDisplayString(MOUSE4)) && "Button 5".equals(GameSettings.getKeyDisplayString(MOUSE5)),
            "Keybinds: the side buttons are named Button 4 and Button 5");
        controls.search().setFocused(true); // the Reset All click left it
        for (char c : "key:button".toCharArray()) CoreProbe.tap(c == ':' ? Keyboard.KEY_SEMICOLON : Keyboard.getKeyIndex(String.valueOf(Character.toUpperCase(c))), c);
        return after(5);
    }

    private static boolean namesShown(Minecraft mc) throws Exception {
        List<KeyBinding> shown = controls.shownKeys();
        check("key:button".equals(controls.search().getText()) && shown.contains(mc.gameSettings.keyBindDrop) && shown.contains(Zoom189.ZOOM)
            && !shown.contains(mc.gameSettings.keyBindForward), "Keybinds: the Controls search 'key:button' lists Drop and "
            + "Lads Zoom on the side buttons (" + shown.size() + " binds on mouse buttons)");
        screenshot(mc, "172-controls-side-buttons");
        mc.displayGuiScreen(null);
        return after(5);
    }

    /** check and hold: every bind of the last run is live and in options.txt. */
    private static boolean expectedLive(Minecraft mc) throws Exception {
        File file = new File(mc.mcDataDir, "lads-qa/172-keybinds-expected.txt");
        check(file.isFile(), "Keybinds: the bind run left " + file.getName());
        Map<String, String> disk = saved(mc);
        List<String> live = new ArrayList<>(), absent = new ArrayList<>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            String[] parts = line.split("=");
            KeyBinding key = byName(mc, parts[0]);
            if (key == null) {
                absent.add(parts[0]);
                check(parts[1].equals(disk.get("key_" + parts[0])), "Keybinds: " + parts[0] + " (its mod is not loaded) is still in options.txt as " + parts[1]);
                continue;
            }
            check(String.valueOf(key.getKeyCode()).equals(parts[1]) && parts[1].equals(disk.get("key_" + parts[0])),
                "Keybinds: " + parts[0] + " is still " + GameSettings.getKeyDisplayString(Integer.parseInt(parts[1])) + " (live " + key.getKeyCode()
                    + ", options.txt " + disk.get("key_" + parts[0]) + ")");
            live.add(parts[0]);
        }
        LOG.info("Lads 1.8.9 keybinds probe: {} binds live after the restart {}; not loaded {}", live.size(), live, absent);
        return after(2);
    }

    /** The QA world's current slot gets 64 dirt (the server's inventory), the player gets in-game focus as with the window's. */
    private static boolean inWorld(Minecraft mc) {
        check(mc.thePlayer != null && mc.currentScreen == null, "Keybinds: in the QA world");
        focusWas = mc.inGameHasFocus;
        mc.inGameHasFocus = true;
        final java.util.UUID id = mc.thePlayer.getUniqueID();
        mc.getIntegratedServer().addScheduledTask(() -> {
            EntityPlayerMP player = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
            player.inventory.setInventorySlotContents(player.inventory.currentItem, new ItemStack(Blocks.dirt, 64));
            player.inventoryContainer.detectAndSendChanges();
        });
        return after(10);
    }

    private static int held(Minecraft mc) {
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        return stack == null ? 0 : stack.stackSize;
    }

    /** Drop on Mouse 4, pressed while sneaking and attacking (Shift and the left button held). */
    private static boolean drop(Minecraft mc) throws Exception {
        heldBefore = held(mc);
        check(heldBefore == 64 && mc.gameSettings.keyBindDrop.getKeyCode() == MOUSE4, "Keybinds: 64 dirt in hand, Drop on Mouse 4");
        EVENTS.seen.clear();
        side(1, true, MK_SHIFT | MK_LBUTTON);
        return after(2);
    }

    private static boolean dropped(Minecraft mc) {
        check(mc.gameSettings.keyBindDrop.isKeyDown() && !Mouse.isButtonDown(4) && EVENTS.seen.equals(Arrays.asList("Mouse 4")),
            "Keybinds: Mouse 4 (Shift and left button held) holds Drop down " + EVENTS.seen);
        return after(5);
    }

    private static boolean dropUp(Minecraft mc) throws Exception {
        side(1, false, MK_SHIFT | MK_LBUTTON);
        check(held(mc) < heldBefore, "Keybinds: Mouse 4 dropped an item (" + heldBefore + " -> " + held(mc) + ")");
        return after(3);
    }

    /** Lads Zoom on Mouse 5 (Hold), pressed while using an item and sprinting (right button and Ctrl held). */
    private static boolean zoom(Minecraft mc) throws Exception {
        check(!mc.gameSettings.keyBindDrop.isKeyDown() && Zoom189.ZOOM.getKeyCode() == MOUSE5 && !Zoom189.zoom().isActive(),
            "Keybinds: Mouse 4's release let go of Drop; Lads Zoom on Mouse 5, not zoomed");
        side(2, true, MK_RBUTTON | MK_CONTROL);
        return after(3);
    }

    private static boolean zoomed(Minecraft mc) throws Exception {
        check(Zoom189.zoom().isActive() && Zoom189.ZOOM.isKeyDown(), "Keybinds: Mouse 5 (right button and Ctrl held) zooms with Lads Zoom");
        side(2, false, MK_RBUTTON | MK_CONTROL);
        return after(3);
    }

    private static boolean zoomUp(Minecraft mc) {
        check(!Zoom189.zoom().isActive() && !Zoom189.ZOOM.isKeyDown(), "Keybinds: releasing Mouse 5 zooms out");
        return after(2);
    }

    /** Toggle Sprint on Mouse 5 for a moment: each press toggles. */
    private static boolean toggle(Minecraft mc) throws Exception {
        mc.gameSettings.setOptionKeyBinding(Toggles189.TOGGLE_SPRINT, MOUSE5);
        KeyBinding.resetKeyBindingArrayAndHash();
        sprintWas = Toggles189.toggles().isEnabled();
        Toggles189.toggles().setEnabled(true); // the module on, for this test
        toggledWas = Toggles189.toggles().isSprintToggled();
        side(2, true, MK_SHIFT);
        side(2, false, MK_SHIFT);
        return after(3);
    }

    private static boolean toggled(Minecraft mc) throws Exception {
        ToggleSprintModule toggles = Toggles189.toggles();
        check(toggles.isSprintToggled() != toggledWas, "Keybinds: Toggle Sprint on Mouse 5 toggles sprint (" + toggles.isSprintToggled() + ")");
        if (toggles.isSprintToggled() != toggledWas && toggles.pressSprint()) ConfigManager.save();
        toggles.setEnabled(sprintWas);
        Zoom189.zoom().release();
        mc.gameSettings.setOptionKeyBinding(Toggles189.TOGGLE_SPRINT, Keyboard.KEY_J);
        KeyBinding.resetKeyBindingArrayAndHash();
        return after(2);
    }

    /** Essential's key on Mouse 5 for a moment (when Essential is loaded); Lads Zoom off it meanwhile: 1.8.9 gives a key code to one binding. */
    private static boolean essentialDown(Minecraft mc) throws Exception {
        if (essential == null) return after(1);
        mc.gameSettings.setOptionKeyBinding(Zoom189.ZOOM, 0);
        mc.gameSettings.setOptionKeyBinding(essential, MOUSE5);
        KeyBinding.resetKeyBindingArrayAndHash();
        side(2, true, MK_LBUTTON);
        return after(3);
    }

    private static boolean essentialUp(Minecraft mc) throws Exception {
        if (essential == null) return after(1);
        check(essential.isKeyDown(), "Keybinds: Essential's " + essential.getKeyDescription() + " on Mouse 5 is down while Mouse 5 is held");
        side(2, false, MK_LBUTTON);
        return after(3);
    }

    private static boolean essentialReleased(Minecraft mc) throws Exception {
        if (essential == null) return after(1);
        check(!essential.isKeyDown(), "Keybinds: Essential's key let go with Mouse 5");
        mc.gameSettings.setOptionKeyBinding(essential, Keyboard.KEY_K);
        mc.gameSettings.setOptionKeyBinding(Zoom189.ZOOM, MOUSE5);
        KeyBinding.resetKeyBindingArrayAndHash();
        return after(2);
    }

    /** OptiFine's zoom on Mouse 4 for a moment, with Lads Zoom off (with it on, Lads Zoom is the only zoom). */
    private static boolean optiFineDown(Minecraft mc) throws Exception {
        if (optiFine == null) return after(1);
        ZoomModule zoom = Zoom189.zoom();
        zoom.setEnabled(false);
        mc.gameSettings.setOptionKeyBinding(optiFine, MOUSE4);
        KeyBinding.resetKeyBindingArrayAndHash();
        baseFov = ((EntityRendererAccessor) mc.entityRenderer).ladsFov(1, true);
        side(1, true, MK_CONTROL | MK_LBUTTON);
        return after(3);
    }

    private static boolean optiFineUp(Minecraft mc) throws Exception {
        if (optiFine == null) return after(1);
        boolean down = GameSettings.isKeyDown(optiFine);
        float zoomed = ((EntityRendererAccessor) mc.entityRenderer).ladsFov(1, true);
        side(1, false, MK_CONTROL | MK_LBUTTON);
        check(down && Math.abs(zoomed - baseFov / 4) < 1e-2,
            "Keybinds: OptiFine's zoom on Mouse 4 (Ctrl and left button held) zooms (FOV " + zoomed + " of " + baseFov + ")");
        return after(3);
    }

    private static boolean writeExpected(Minecraft mc) throws Exception {
        if (optiFine != null && "bind".equals(PHASE)) {
            float back = ((EntityRendererAccessor) mc.entityRenderer).ladsFov(1, true);
            check(!GameSettings.isKeyDown(optiFine) && Math.abs(back - baseFov) < 1e-2, "Keybinds: releasing Mouse 4 ends OptiFine's zoom (FOV " + back + ")");
            mc.gameSettings.setOptionKeyBinding(optiFine, Keyboard.KEY_N);
            KeyBinding.resetKeyBindingArrayAndHash();
            Zoom189.zoom().setEnabled(true);
        }
        List<String> lines = new ArrayList<>();
        List<KeyBinding> keys = new ArrayList<>(Arrays.asList(mc.gameSettings.keyBindDrop, Zoom189.ZOOM, mc.gameSettings.keyBindJump,
            Toggles189.TOGGLE_SPRINT, mc.gameSettings.keyBindChat));
        if (optiFine != null) keys.add(optiFine);
        if (essential != null) keys.add(essential);
        for (KeyBinding key : keys) {
            check(onDisk(mc, key, key.getKeyCode()), "Keybinds: options.txt has " + key.getKeyDescription() + " as " + key.getKeyCode());
            lines.add(key.getKeyDescription() + "=" + key.getKeyCode());
        }
        // A bind of a mod not loaded in this run stays expected.
        File file = new File(mc.mcDataDir, "lads-qa/172-keybinds-expected.txt");
        if (file.isFile()) for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
            if (byName(mc, line.split("=")[0]) == null) lines.add(line);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        LOG.info("Lads 1.8.9 keybinds probe: expected binds {}", lines);
        return after(2);
    }

    private static boolean waitForKill(Minecraft mc) {
        LOG.info("Lads 1.8.9 keybinds probe: waiting in the QA world to be killed");
        return CoreProbe.retry(20);
    }

    private static boolean finish(Minecraft mc) {
        stop();
        return after(1);
    }

    /** Also when a step failed. */
    static void stop() {
        if (!started) return;
        started = false;
        Minecraft mc = Minecraft.getMinecraft();
        MinecraftForge.EVENT_BUS.unregister(EVENTS);
        Zoom189.synthetic = false;
        Zoom189.zoom().setEnabled(zoomWas);
        mc.inGameHasFocus = focusWas;
        SideButtons189.install();
        try {
            capture(-1);
        } catch (ReflectiveOperationException ignored) {}
    }

    /**
     * Back to back until the game is killed: Minecraft's saveOptions, then the same lines written as vanilla wrote them. Meanwhile
     * another thread reads both files as fast as it can and counts the reads that found a cut-off file (what a kill at that
     * moment leaves behind).
     */
    private static boolean storm(Minecraft mc) throws Exception {
        File plain = new File(mc.mcDataDir, "lads-qa/options-plain.txt");
        if (saves == 0) mc.gameSettings.saveOptions(); // the file as Minecraft writes it (the harness adds lines it drops)
        List<String> lines = Files.readAllLines(options(mc).toPath(), Charset.defaultCharset());
        if (saves == 0) {
            plain.getParentFile().mkdirs();
            int whole = lines.size();
            Thread reader = new Thread(() -> {
                while (true) {
                    int n = lineCount(options(mc));
                    if (n < 0) unreadable++; else if (n < whole) optionsCut++;
                    reads++;
                    n = lineCount(plain);
                    if (n >= 0 && n < whole) plainCut++;
                }
            }, "Lads QA options reader");
            reader.setDaemon(true);
            reader.start();
            LOG.info("Lads 1.8.9 keybinds probe: saving options back to back until killed ({} lines)", whole);
        }
        long end = System.nanoTime() + 40_000_000L;
        while (System.nanoTime() < end) {
            mc.gameSettings.saveOptions();
            PrintWriter writer = new PrintWriter(new FileWriter(plain));
            for (String line : lines) writer.println(line);
            writer.close();
            if (++saves % 2000 == 0) LOG.info("Lads 1.8.9 keybinds probe: {} saves; {} reads of each file: options.txt cut off {} times "
                + "(unreadable {} times), the vanilla-style copy cut off {} times", saves, reads, optionsCut, unreadable, plainCut);
        }
        return CoreProbe.retry(0);
    }

    private static volatile long reads, optionsCut, unreadable, plainCut;

    private static int lineCount(File file) {
        try {
            return Files.readAllLines(file.toPath(), Charset.defaultCharset()).size();
        } catch (Exception e) {
            return -1;
        }
    }

    private static void press(String label) throws Exception {
        GuiButton button = controls.button(label);
        check(button != null && button.enabled, "Keybinds: the '" + label + "' button is there");
        CoreProbe.click(button.xPosition + button.width / 2, button.yPosition + button.height / 2);
    }
}
