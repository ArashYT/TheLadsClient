package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.retry;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.thelads.core.client.BorderlessWindow;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * QA only: the 1.4.5 checks, run by CoreProbe in its QA world after HudProbe. HUD frame rate, the camera's mouse input, frame
 * pacing's cost, SmoothHotbar, LegacySwing, borderless fullscreen and a freely resized window, each through the real 1.8.9 paths.
 * With LADS_VERIFY_CAPTURE_WINDOW set, borderless fullscreen is also captured the way OBS and Discord see it, once as it is (monitor
 * plus the extra row) and once as an exact monitor-sized window, which Windows promotes to flip and capture tools see as black.
 */
final class Probe145 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int WS_CAPTION = 0x00C00000, WS_THICKFRAME = 0x00040000, GWL_STYLE = -16;
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe145::rateStart, Probe145::rate, Probe145::mouse,
        Probe145::hotbarStart, Probe145::hotbarGlide, Probe145::hotbarMoving,
        Probe145::hotbarSettled, Probe145::swingStart,
        Probe145::swingLegacy, Probe145::swingSwitch, Probe145::swingVanilla, Probe145::borderless, Probe145::borderlessCaptured, Probe145::borderlessExact, Probe145::borderlessExactCaptured, Probe145::windowed, Probe145::resized, Probe145::resizedShot,
        Probe145::restored);
    /** Frame pacing's cost, a timing check: CoreProbe runs it last, so a busy GPU (another game open) cannot hide the checks after it. */
    static final List<CoreProbe.Step> PACING = Arrays.<CoreProbe.Step>asList(Probe145::pacedStart, Probe145::pacedCount, Probe145::paced,
        Probe145::unpaced, Probe145::pacedAgain);
    private static long frames, since;
    private static float pacedFps, unpacedFps;
    private static int limit;
    private static boolean vsync;
    private static ItemStack[] hotbar;
    private static long swingFrames;
    private static int windowWidth, windowHeight;
    private static WinDef.RECT outer;
    /** QA: tools/capture_window_check.ps1, named by LADS_VERIFY_CAPTURE_WINDOW. It captures the window as OBS and Discord do. */
    private static final String CAPTURE = System.getenv("LADS_VERIFY_CAPTURE_WINDOW");
    private static Process capture;
    private static File captureLog;
    private static long captureStarted;

    private Probe145() {}

    private static float fps() {
        return (NativeHud.frames - frames) * 1e9f / (System.nanoTime() - since);
    }

    private static void startCount() {
        frames = NativeHud.frames;
        since = System.nanoTime();
    }

    private static boolean rateStart(Minecraft mc) {
        if (mc.currentScreen != null) return false;
        startCount();
        return after(60);
    }

    /** The Lads HUD with the HUD FPS cap as configured (off by default): rebuilt in every frame, not every second one. */
    private static boolean rate(Minecraft mc) {
        float drawn = fps();
        int built = HudManager.getInstance().getMeasuredHudFps();
        HudSettings settings = HudSettings.getInstance();
        LOG.info("Lads 1.8.9 core probe: HUD rate: drawn in {} frames/s, rebuilt {} times/s, game {} FPS (debug), cap {} at {}, vsync {}, limit {}",
            drawn, built, Minecraft.getDebugFPS(), settings.isHudFpsCapEnabled(), settings.getHudFpsLimit(), mc.gameSettings.enableVsync,
            mc.gameSettings.limitFramerate);
        check(!settings.isHudFpsCapEnabled() && built >= drawn * 0.8f, "the HUD FPS cap is off by default (the sandbox config was "
            + "saved by 1.4.4 with its default on at 60) and the Lads HUD is rebuilt in every frame: " + built + " of " + drawn + " frames/s");
        return after(1);
    }

    /** The camera's mouse input: the Lads MouseHelper, LWJGL's deltas while no JInput mouse reports motion (the frozen-camera bug). */
    private static boolean mouse(Minecraft mc) throws Exception {
        check(mc.mouseHelper instanceof RawMouse189, "the Lads mouse helper reads the camera's mouse input (" + mc.mouseHelper.getClass().getName() + ")");
        RawMouse189 helper = (RawMouse189) mc.mouseHelper;
        Field dx = Mouse.class.getDeclaredField("dx"), dy = Mouse.class.getDeclaredField("dy");
        dx.setAccessible(true);
        dy.setAccessible(true);
        Mouse.getDX(); // drop what real input left
        Mouse.getDY();
        dx.setInt(null, 37);
        dy.setInt(null, -11);
        helper.mouseXYChange();
        // An ungrabbed mouse (QA window without focus) makes Mouse.poll() replace the injected deltas with the cursor's own.
        LOG.info("Lads 1.8.9 core probe: mouse: RawInput {}, {} JInput mice, raw live {}, frame pacing {}, window focused {}, mouse grabbed {}",
            module("RawInput").isEnabled(), helper.mice == null ? "no scan of" : String.valueOf(helper.mice.size()), helper.rawLive,
            RawMouse189.pacing, Display.isActive(), Mouse.isGrabbed());
        check(helper.rawLive || (helper.deltaX == 37 && helper.deltaY == -11),
            "with Raw Input on and no JInput mouse moving, the camera turns by LWJGL's deltas (37, -11): " + helper.deltaX + ", " + helper.deltaY);
        check(RawMouse189.pacing, "frame pacing is on (GL 3.2 fences)");
        return after(1);
    }

    /** Frame pacing's cost: the frame rate without VSync or a limit, with and without pacing. */
    private static boolean pacedStart(Minecraft mc) {
        vsync = mc.gameSettings.enableVsync;
        limit = mc.gameSettings.limitFramerate;
        mc.gameSettings.enableVsync = false;
        Display.setVSyncEnabled(false);
        mc.gameSettings.limitFramerate = (int) GameSettings.Options.FRAMERATE_LIMIT.getValueMax();
        return after(20); // the new limit settles before counting
    }

    private static boolean pacedCount(Minecraft mc) {
        startCount();
        return after(60);
    }

    private static boolean paced(Minecraft mc) {
        pacedFps = fps();
        RawMouse189.pacing = false;
        startCount();
        return after(60);
    }

    /** Paced, unpaced, then paced again: the better paced window, so neither side gets only the noisy one. */
    private static boolean unpaced(Minecraft mc) {
        unpacedFps = fps();
        RawMouse189.pacing = true;
        startCount();
        return after(60);
    }

    private static boolean pacedAgain(Minecraft mc) {
        pacedFps = Math.max(pacedFps, fps());
        float unpaced = unpacedFps;
        mc.gameSettings.enableVsync = vsync;
        Display.setVSyncEnabled(vsync);
        mc.gameSettings.limitFramerate = limit;
        LOG.info("Lads 1.8.9 core probe: frame pacing: {} FPS paced, {} FPS unpaced (no VSync, no limit)", pacedFps, unpaced);
        // Thousands of FPS: a percentage measures noise there, so pacing may also cost under 0.1 ms a frame.
        check(pacedFps >= unpaced * 0.85f || 1000f / pacedFps - 1000f / unpaced < 0.1f,
            "frame pacing keeps the frame rate: " + pacedFps + " FPS paced, " + unpaced + " unpaced");
        return after(1);
    }

    private static boolean hotbarStart(Minecraft mc) {
        hotbar = mc.thePlayer.inventory.mainInventory.clone();
        mc.thePlayer.inventory.mainInventory[0] = new ItemStack(Items.diamond_sword);
        mc.thePlayer.inventory.currentItem = 0;
        module("SmoothHotbar").setEnabled(true);
        return after(5);
    }

    private static boolean hotbarGlide(Minecraft mc) {
        check(NativeHud.selectionOffset == 0, "SmoothHotbar: the frame rests on slot 1");
        mc.thePlayer.inventory.currentItem = 8;
        return after(1);
    }

    private static boolean hotbarMoving(Minecraft mc) {
        check(NativeHud.selectionOffset < -5, "SmoothHotbar: a tick later the selection frame is still on its way to slot 9 ("
            + NativeHud.selectionOffset + " GUI px from it)");
        screenshot(mc, "145-smooth-hotbar-gliding");
        return after(20);
    }

    private static boolean hotbarSettled(Minecraft mc) {
        check(NativeHud.selectionOffset == 0, "SmoothHotbar: the frame settles on slot 9");
        // Back to the sword: the hand lowers and pulls it out (a few ticks) before it can swing.
        mc.thePlayer.inventory.currentItem = 0;
        return after(10);
    }

    private static boolean swingStart(Minecraft mc) {
        module("LegacySwing").setEnabled(true);
        swingFrames = LegacySwing189.frames;
        mc.thePlayer.swingItem();
        return after(2);
    }

    private static boolean swingLegacy(Minecraft mc) {
        check(LegacySwing189.frames > swingFrames, "LegacySwing: the sword swings with the legacy motion in " + (LegacySwing189.frames - swingFrames) + " frames");
        screenshot(mc, "145-legacy-swing-on");
        mc.thePlayer.inventory.mainInventory[1] = new ItemStack(Items.apple);
        mc.thePlayer.inventory.currentItem = 1;
        return after(2);
    }

    private static boolean swingSwitch(Minecraft mc) {
        float equip = ((com.thelads.core.v1_8_9.mixin.ItemRendererAccessor) mc.getItemRenderer()).getEquippedProgress();
        check(equip < 0.5f, "LegacySwing: switching items still lowers the hand to pull the next one out (raised " + equip + ")");
        mc.thePlayer.inventory.currentItem = 0;
        module("LegacySwing").setEnabled(false);
        swingFrames = LegacySwing189.frames;
        mc.thePlayer.swingItem();
        return after(2);
    }

    private static boolean swingVanilla(Minecraft mc) throws Exception {
        check(LegacySwing189.frames == swingFrames, "LegacySwing off: the vanilla 1.8.9 swing");
        screenshot(mc, "145-legacy-swing-off");
        System.arraycopy(hotbar, 0, mc.thePlayer.inventory.mainInventory, 0, hotbar.length);
        // Borderless: F11's path (Minecraft.toggleFullscreen) with the module on.
        module("BorderlessFullscreen").setEnabled(true);
        windowWidth = Display.getWidth();
        windowHeight = Display.getHeight();
        outer = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(hwnd(), outer);
        mc.toggleFullscreen();
        return after(20);
    }

    private static boolean borderless(Minecraft mc) throws Exception {
        DisplayMode monitor = Display.getDesktopDisplayMode();
        int style = User32.INSTANCE.GetWindowLong(hwnd(), GWL_STYLE);
        int height = monitor.getHeight() + BorderlessWindow.EXTRA_HEIGHT;
        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(hwnd(), rect);
        check(!Display.isFullscreen() && mc.isFullScreen() && mc.gameSettings.fullScreen, "borderless: Minecraft is fullscreen, LWJGL's window is not exclusive (display "
            + Display.isFullscreen() + ", mc " + mc.isFullScreen() + ", option " + mc.gameSettings.fullScreen + ", borderless " + Borderless189.active() + ")");
        check(Display.getX() == 0 && Display.getY() == 0 && Display.getWidth() == monitor.getWidth() && Display.getHeight() == height
            && mc.displayWidth == monitor.getWidth() && mc.displayHeight == height, "borderless: the window covers the " + monitor.getWidth() + "x"
            + monitor.getHeight() + " monitor at 0,0 plus " + BorderlessWindow.EXTRA_HEIGHT + " off-screen row, so Windows keeps it a desktop window that "
            + "OBS and Discord can capture (" + Display.getX() + "," + Display.getY() + " " + Display.getWidth() + "x" + Display.getHeight() + ", game "
            + mc.displayWidth + "x" + mc.displayHeight + ")");
        check(rect.left == 0 && rect.top == 0 && rect.right == monitor.getWidth() && rect.bottom == height, "borderless: the native window rect is "
            + rect.left + "," + rect.top + " to " + rect.right + "," + rect.bottom);
        check((style & (WS_CAPTION | WS_THICKFRAME)) == 0, "borderless: no title bar or resize frame (style 0x" + Integer.toHexString(style) + ")");
        ScaledResolution gui = new ScaledResolution(mc);
        check(gui.getScaledHeight() * gui.getScaleFactor() <= monitor.getHeight(), "borderless: the GUI is laid out in the visible " + monitor.getHeight()
            + " rows, so the hotbar is not under the extra row (" + gui.getScaledHeight() + " GUI px x " + gui.getScaleFactor() + ")");
        screenshot(mc, "145-borderless");
        startCapture("borderless-extra-row");
        return after(1);
    }

    private static boolean borderlessCaptured(Minecraft mc) throws Exception {
        if (!captureDone("borderless-extra-row", true)) return retry(5);
        if (CAPTURE == null) {
            mc.toggleFullscreen();
            return after(5);
        }
        // The window as 1.7.0 and older made it, exactly the monitor, for comparison.
        DisplayMode monitor = Display.getDesktopDisplayMode();
        Display.setDisplayMode(new DisplayMode(monitor.getWidth(), monitor.getHeight()));
        mc.resize(Display.getWidth(), Display.getHeight());
        return after(60);
    }

    private static boolean borderlessExact(Minecraft mc) throws Exception {
        if (CAPTURE == null) return true;
        startCapture("borderless-exact-monitor");
        return after(1);
    }

    private static boolean borderlessExactCaptured(Minecraft mc) throws Exception {
        if (CAPTURE == null) return true;
        if (!captureDone("borderless-exact-monitor", false)) return retry(5);
        mc.toggleFullscreen();
        return after(5);
    }

    private static boolean windowed(Minecraft mc) throws Exception {
        int style = User32.INSTANCE.GetWindowLong(hwnd(), GWL_STYLE);
        check(!mc.isFullScreen() && !mc.gameSettings.fullScreen && Display.getWidth() == windowWidth && Display.getHeight() == windowHeight,
            "F11 again: back to the " + windowWidth + "x" + windowHeight + " window (" + Display.getWidth() + "x" + Display.getHeight() + ")");
        check((style & WS_CAPTION) == WS_CAPTION && (style & WS_THICKFRAME) != 0 && Display.isResizable(),
            "the window has its title bar and resize frame again (style 0x" + Integer.toHexString(style) + ")");
        // A resize as the user drags the frame: Windows sizes the window, Minecraft follows.
        User32.INSTANCE.SetWindowPos(hwnd(), null, 0, 0, 701, 397, 0x16); // SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE
        return after(5);
    }

    private static boolean resized(Minecraft mc) throws Exception {
        check(mc.displayWidth == Display.getWidth() && mc.displayHeight == Display.getHeight() && mc.displayWidth != windowWidth
            && mc.displayWidth < 701 && mc.displayHeight < 397, "an odd window size (701x397 outside): the game follows at "
            + mc.displayWidth + "x" + mc.displayHeight);
        return after(10);
    }

    /** The screenshot reads Minecraft's framebuffer, which the resize recreated: a few frames later it holds a whole frame. */
    private static boolean resizedShot(Minecraft mc) throws Exception {
        screenshot(mc, "145-window-701x397");
        User32.INSTANCE.SetWindowPos(hwnd(), null, outer.left, outer.top, outer.right - outer.left, outer.bottom - outer.top, 0x14);
        return after(5);
    }

    private static boolean restored(Minecraft mc) {
        check(mc.displayWidth == windowWidth && mc.displayHeight == windowHeight, "and back at " + mc.displayWidth + "x" + mc.displayHeight);
        return true;
    }

    private static WinDef.HWND hwnd() throws Exception {
        return Borderless189.hwnd();
    }

    private static long hwndValue() throws Exception {
        return Pointer.nativeValue(hwnd().getPointer());
    }

    /** Runs the capture script on the window in the background (it needs the game's frames to go on); captureDone waits for it. */
    private static void startCapture(String tag) throws Exception {
        if (CAPTURE == null) return;
        File folder = new File(new File(Minecraft.getMinecraft().mcDataDir, "lads-qa"), "capture");
        folder.mkdirs();
        captureLog = new File(folder, tag + ".log");
        capture = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", CAPTURE, "-Hwnd", Long.toString(hwndValue()),
            "-OutDir", folder.getAbsolutePath(), "-Tag", tag).redirectErrorStream(true).redirectOutput(captureLog).start();
        captureStarted = System.nanoTime();
    }

    /** The capture finished (or none was asked for). It must show the game, never black, when {@code required}; otherwise it is only logged. */
    private static boolean captureDone(String tag, boolean required) throws Exception {
        if (capture == null) return true;
        if (capture.isAlive()) {
            if (System.nanoTime() - captureStarted > 90_000_000_000L) throw new IllegalStateException("1.8.9 core QA: the window capture did not finish within 90 s");
            return false;
        }
        capture = null;
        String report = new String(Files.readAllBytes(captureLog.toPath()), StandardCharsets.UTF_8);
        for (String line : report.split("\r?\n")) LOG.info("Lads 1.8.9 core probe: window capture {}: {}", tag, line);
        if (required) {
            check(report.contains("bitblt ") && report.contains("printwindow ") && report.contains("duplicate ") && report.contains("wgc ") && !report.contains("FAILED") && !report.contains("-> BLACK"),
                "borderless: GDI (BitBlt), window (PrintWindow), DXGI screen and Windows Graphics Capture all show the game, none is black");
        }
        return true;
    }

    private static Module module(String name) {
        return ModuleManager.getInstance().getModule(name);
    }
}
