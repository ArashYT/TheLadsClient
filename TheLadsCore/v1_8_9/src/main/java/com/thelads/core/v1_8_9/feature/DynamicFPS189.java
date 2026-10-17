package com.thelads.core.v1_8_9.feature;

import com.thelads.core.client.BackgroundFps;
import com.thelads.core.client.BackgroundFps.State;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Dynamic FPS (1.9.11), as 26.x's: the window's background state (BackgroundFps) sets the frame rate limit through
 * Minecraft.getLimitFramerate and the world render skip (MinecraftMixin) and fades the master volume. Unfocused: the Unfocused FPS
 * slider and 25% volume; under the mouse: 60; minimized: the Hidden FPS slider, no sound and no world render; focused with no input
 * for 5 minutes: 10. Never raises the player's own limit. Background Profiles (DynamicFpsScreen189) edits the volumes and idle profile,
 * kept in config/theladscore-dynamicfps.properties. Replaces 1.7.3's Unfocused FPS cap (ConfigManager.migrateUnfocusedCap).
 */
public final class DynamicFPS189 {
    public static final String NAME = "DynamicFPS";
    /** The QA harness sets this so its (never focused) game windows keep the FPS the probes and benchmarks ask for; never in the benchmark. */
    static boolean bypass = Boolean.getBoolean("thelads.noUnfocusedCap") || "perf".equals(System.getProperty("thelads.verify189Only"));
    /** QA only: count the window as unfocused but visible and not under the mouse (forceUnfocused), or minimized (forceHidden). */
    static boolean forceUnfocused, forceHidden;
    public static final BackgroundFps.Profile PROFILE = new BackgroundFps.Profile();
    private static long unfocusedSince = -1, lastInput = System.currentTimeMillis();
    private static int lastMode = -1, mouseX, mouseY;
    private static float lastLook, volume = 1f, applied = -1f;

    private DynamicFPS189() {}

    /** Built in through TheLadsCore189.GAMEPLAY_MODULES; Background Profiles opens the 1.8.9 profile editor. */
    public static void register() {
        load();
        ((ActionOption) module().getOption("Background Profiles")).setAction(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            mc.displayGuiScreen(new com.thelads.core.v1_8_9.gui.DynamicFpsScreen189(mc.currentScreen));
        });
    }

    static Module module() { return ModuleManager.getInstance().getModule(NAME); }

    private static int mode() { return Options189.choice(NAME, "Mode", BackgroundFps.BALANCED); }

    /** The window's state this frame (FOCUSED while the module is off, its Mode is Off, or for the QA harness). */
    public static State state() {
        Module module = module();
        if (bypass || module == null || !module.isEnabled()) return State.FOCUSED;
        boolean hidden = forceHidden || !forceUnfocused && !Display.isVisible();
        boolean focused = !forceUnfocused && !forceHidden && Display.isActive();
        long now = System.currentTimeMillis();
        return BackgroundFps.state(mode(), focused, !hidden, !forceUnfocused && Mouse.isInsideWindow(),
            unfocusedSince < 0 ? 0 : now - unfocusedSince, now - lastInput, PROFILE.idleMinutes);
    }

    private static int target(State state) {
        return BackgroundFps.target(state, (int) Options189.number(NAME, "Unfocused FPS", 60), (int) Options189.number(NAME, "Hidden FPS", 30), PROFILE);
    }

    /** Minecraft's frame rate limit for this frame, given the one it would use. */
    public static int limit(int vanilla) {
        State state = state();
        if (state == State.FOCUSED) return vanilla;
        int target = target(state);
        // ponytail: 1.8.9 ticks inside the render loop (at most 10 ticks a frame), so a suspended or tiny target still loops 10 or 2
        // times a second to keep 20 ticks; a separate tick thread would be needed for a true 0.
        return target == BackgroundFps.SUSPEND ? 10 : Math.max(2, BackgroundFps.limit(target, vanilla));
    }

    /** Whether the frame skips rendering: minimized (nothing to see) or a 0 FPS target. */
    public static boolean shouldSkipWorldRender(boolean vanillaSkip) {
        if (vanillaSkip) return true;
        State state = state();
        return state == State.INVISIBLE || state != State.FOCUSED && target(state) == BackgroundFps.SUSPEND;
    }

    /** Client tick: Mode presets, focus and input times, the volume fade. */
    public static void tick(Minecraft mc) {
        int mode = mode();
        if (lastMode >= 0 && mode != lastMode && mode != BackgroundFps.OFF) {
            int[] preset = BackgroundFps.preset(mode);
            ((SliderOption) module().getOption("Unfocused FPS")).setValue(preset[0]);
            ((SliderOption) module().getOption("Hidden FPS")).setValue(preset[1]);
        }
        lastMode = mode;
        long now = System.currentTimeMillis();
        if (input(mc)) lastInput = now;
        if (Display.isActive() && !forceUnfocused && !forceHidden) unfocusedSince = -1;
        else if (unfocusedSince < 0) unfocusedSince = now;
        volume = BackgroundFps.fade(volume, BackgroundFps.volume(state(), PROFILE), 0.05f);
        float wanted = mc.gameSettings.getSoundLevel(SoundCategory.MASTER) * volume;
        if (Math.abs(wanted - applied) > 1e-4f) {
            // The sound system's master volume only: the saved option stays, and 0 does not stop the music (SoundHandler.setSoundLevel would).
            ((com.thelads.core.v1_8_9.mixin.SoundHandlerAccessor189) mc.getSoundHandler()).ladsSoundManager().setSoundCategoryVolume(SoundCategory.MASTER, wanted);
            applied = wanted;
        }
    }

    /** QA: force a background state; Balanced's 3 s grace starts again from the next tick. */
    static void force(boolean unfocused, boolean hidden) {
        forceUnfocused = unfocused;
        forceHidden = hidden;
        unfocusedSince = -1;
    }

    /** QA: set the Mode without its slider preset (Probe173Cap keeps its own sliders). */
    static void qaMode(int mode) {
        ((com.thelads.core.config.DropdownOption) module().getOption("Mode")).setIndex(mode);
        lastMode = mode;
    }

    /** The current master volume multiplier (QA). */
    static float volume() { return volume; }

    /** Any mouse movement, look change, button or key since the last tick (the idle profile). */
    private static boolean input(Minecraft mc) {
        int x = Mouse.getX(), y = Mouse.getY();
        float look = mc.thePlayer == null ? 0 : mc.thePlayer.rotationYaw * 31 + mc.thePlayer.rotationPitch;
        boolean moved = x != mouseX || y != mouseY || look != lastLook;
        mouseX = x;
        mouseY = y;
        lastLook = look;
        if (moved) return true;
        for (int button = 0; button < Mouse.getButtonCount(); button++) if (Mouse.isButtonDown(button)) return true;
        for (int key = 1; key < Keyboard.KEYBOARD_SIZE; key++) if (Keyboard.isKeyDown(key)) return true;
        return false;
    }

    private static File file() { return new File(Minecraft.getMinecraft().mcDataDir, "config/theladscore-dynamicfps.properties"); }

    static void load() {
        Properties saved = new Properties();
        File file = file();
        if (file.isFile()) try (InputStream in = new FileInputStream(file)) {
            saved.load(in);
            PROFILE.unfocusedVolume = Float.parseFloat(saved.getProperty("unfocusedVolume", "0.25"));
            PROFILE.hiddenVolume = Float.parseFloat(saved.getProperty("hiddenVolume", "0"));
            PROFILE.idleFps = Integer.parseInt(saved.getProperty("idleFps", "10"));
            PROFILE.idleMinutes = Integer.parseInt(saved.getProperty("idleMinutes", "5"));
        } catch (IOException | RuntimeException e) {
            LogManager.getLogger("TheLadsCore").warn("Dynamic FPS: could not read {}, using the default profiles", file, e);
        }
        PROFILE.clamp();
    }

    public static void save() {
        PROFILE.clamp();
        Properties saved = new Properties();
        saved.setProperty("unfocusedVolume", Float.toString(PROFILE.unfocusedVolume));
        saved.setProperty("hiddenVolume", Float.toString(PROFILE.hiddenVolume));
        saved.setProperty("idleFps", Integer.toString(PROFILE.idleFps));
        saved.setProperty("idleMinutes", Integer.toString(PROFILE.idleMinutes));
        File file = file();
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            saved.store(out, "The Lads Client: Dynamic FPS background profiles (1.8.9)");
        } catch (IOException e) {
            LogManager.getLogger("TheLadsCore").warn("Dynamic FPS: could not save {}", file, e);
        }
    }
}
