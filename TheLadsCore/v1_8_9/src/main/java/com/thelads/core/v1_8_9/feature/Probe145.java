package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
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
 */
final class Probe145 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final int WS_CAPTION = 0x00C00000, WS_THICKFRAME = 0x00040000, GWL_STYLE = -16;
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe145::rateStart, Probe145::rate, Probe145::mouse,
        Probe145::pacedStart, Probe145::paced, Probe145::unpaced, Probe145::hotbarStart, Probe145::hotbarGlide, Probe145::hotbarMoving,
        Probe145::hotbarSettled,
        Probe145::swingLegacy, Probe145::swingVanilla, Probe145::borderless, Probe145::windowed, Probe145::resized, Probe145::resizedShot,
        Probe145::restored);
    private static long frames, since;
    private static float pacedFps;
    private static int limit;
    private static boolean vsync;
    private static ItemStack[] hotbar;
    private static long swingFrames;
    private static int windowWidth, windowHeight;
    private static WinDef.RECT outer;

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
        LOG.info("Lads 1.8.9 core probe: mouse: RawInput {}, {} JInput mice, raw live {}, frame pacing {}", module("RawInput").isEnabled(),
            helper.mice == null ? "no scan of" : String.valueOf(helper.mice.size()), helper.rawLive, RawMouse189.pacing);
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
        startCount();
        return after(60);
    }

    private static boolean paced(Minecraft mc) {
        pacedFps = fps();
        RawMouse189.pacing = false;
        startCount();
        return after(60);
    }

    private static boolean unpaced(Minecraft mc) {
        float unpaced = fps();
        RawMouse189.pacing = true;
        mc.gameSettings.enableVsync = vsync;
        Display.setVSyncEnabled(vsync);
        mc.gameSettings.limitFramerate = limit;
        LOG.info("Lads 1.8.9 core probe: frame pacing: {} FPS paced, {} FPS unpaced (no VSync, no limit)", pacedFps, unpaced);
        check(pacedFps >= unpaced * 0.85f, "frame pacing keeps the frame rate: " + pacedFps + " FPS paced, " + unpaced + " unpaced");
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
        mc.thePlayer.inventory.currentItem = 0;
        module("LegacySwing").setEnabled(true);
        swingFrames = LegacySwing189.frames;
        mc.thePlayer.swingItem();
        return after(2);
    }

    private static boolean swingLegacy(Minecraft mc) {
        check(LegacySwing189.frames > swingFrames, "LegacySwing: the sword swings with the legacy motion in " + (LegacySwing189.frames - swingFrames) + " frames");
        screenshot(mc, "145-legacy-swing-on");
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
        check(!Display.isFullscreen() && mc.isFullScreen() && mc.gameSettings.fullScreen, "borderless: Minecraft is fullscreen, LWJGL's window is not exclusive");
        check(Display.getX() == 0 && Display.getY() == 0 && Display.getWidth() == monitor.getWidth() && Display.getHeight() == monitor.getHeight()
            && mc.displayWidth == monitor.getWidth() && mc.displayHeight == monitor.getHeight(), "borderless: the window covers the "
            + monitor.getWidth() + "x" + monitor.getHeight() + " monitor at 0,0 (" + Display.getX() + "," + Display.getY() + " "
            + Display.getWidth() + "x" + Display.getHeight() + ", game " + mc.displayWidth + "x" + mc.displayHeight + ")");
        check((style & (WS_CAPTION | WS_THICKFRAME)) == 0, "borderless: no title bar or resize frame (style 0x" + Integer.toHexString(style) + ")");
        screenshot(mc, "145-borderless");
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
        Field impl = Display.class.getDeclaredField("display_impl");
        impl.setAccessible(true);
        Object display = impl.get(null);
        Field hwnd = display.getClass().getDeclaredField("hwnd");
        hwnd.setAccessible(true);
        return new WinDef.HWND(new Pointer(hwnd.getLong(display)));
    }

    private static Module module(String name) {
        return ModuleManager.getInstance().getModule(name);
    }
}
