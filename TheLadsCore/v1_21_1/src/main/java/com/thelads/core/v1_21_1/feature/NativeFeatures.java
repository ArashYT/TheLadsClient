package com.thelads.core.v1_21_1.feature;

import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.modules.ToggleSneakModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.modules.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import org.lwjgl.glfw.GLFW;

/** Client-thread state. Never writes vanilla options or bypasses vanilla movement eligibility. Same rules as 1.21.11 and 26.x. */
public final class NativeFeatures {
    private static Object player;
    private static boolean sprintHeld, sneakHeld, sprintRequested;
    /** QA: the world FOV of the last rendered frame, zoom included, and when Zoom computed it. */
    static double lastWorldFov;
    static long lastWorldNanos;

    private NativeFeatures() {}

    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }
    private static ZoomModule zoom() { return (ZoomModule) module("Zoom"); }
    public static boolean enabled(String name) {
        Module module = module(name);
        return module != null && module.isEnabled();
    }
    private static int mode(Module module) {
        return module.getOption("Mode") instanceof DropdownOption value ? value.getIndex() : 0;
    }
    public static boolean interactive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && !mc.player.isDeadOrDying()
            && mc.screen == null && NativeWorldVerification.windowActive() && !mc.isPaused();
    }
    public static void reset(boolean clearToggles) {
        if (clearToggles) {
            stopOwnedSprint();
            if (module("ToggleSprint") instanceof ToggleSprintModule sprint && sprint.isToggled()) sprint.onToggleKey();
            if (module("ToggleSneak") instanceof ToggleSneakModule sneak && sneak.isToggled()) sneak.onToggleKey();
        }
        // Another player or world starts unzoomed; anything else (a screen, focus loss) zooms out smoothly.
        if (clearToggles) zoom().reset(); else zoom().release();
        sprintHeld = sneakHeld = false;
    }
    public static void reset() {
        reset(false);
    }
    public static void tick() {
        Object currentPlayer = Minecraft.getInstance().player;
        if (currentPlayer != player) {
            reset(true);
            player = currentPlayer;
            return;
        }
        if (!interactive()) {
            zoom().release();
            return;
        }
        if (!enabled("ToggleSprint") && module("ToggleSprint") instanceof ToggleSprintModule sprint) {
            stopOwnedSprint();
            sprint.evaluateSprint(false);
            sprintHeld = false;
        }
        if (!enabled("ToggleSneak") && module("ToggleSneak") instanceof ToggleSneakModule sneak) {
            sneak.evaluateSneak(false);
            sneakHeld = false;
        }
    }
    public static void key(int key, int scancode, int action) {
        if (action == GLFW.GLFW_REPEAT) return;
        if (!interactive()) { reset(); return; }
        boolean down = action == GLFW.GLFW_PRESS;
        Minecraft mc = Minecraft.getInstance();
        if (NativeKeyBindings.ZOOM.matches(key, scancode)) zoom().key(down);
        if (mc.options.keySprint.matches(key, scancode)) sprintKey(down);
        if (mc.options.keyShift.matches(key, scancode)) sneakKey(down);
    }
    public static void mouse(int button, int action) {
        if (!interactive()) { reset(); return; }
        boolean down = action == GLFW.GLFW_PRESS;
        Minecraft mc = Minecraft.getInstance();
        if (NativeKeyBindings.ZOOM.matchesMouse(button)) zoom().key(down);
        if (mc.options.keySprint.matchesMouse(button)) sprintKey(down);
        if (mc.options.keyShift.matchesMouse(button)) sneakKey(down);
    }
    private static void sprintKey(boolean down) {
        if (down && !sprintHeld && module("ToggleSprint") instanceof ToggleSprintModule sprint
            && sprint.isEnabled() && mode(sprint) == 0) sprint.onToggleKey();
        sprintHeld = down;
    }
    private static void sneakKey(boolean down) {
        if (down && !sneakHeld && module("ToggleSneak") instanceof ToggleSneakModule sneak
            && sneak.isEnabled() && mode(sneak) == 0) sneak.onToggleKey();
        sneakHeld = down;
    }
    /** KeyboardInput.tick TAIL. 1.21.1's Input is mutable and has no sprint flag, so the sprint request waits for sprintInput. */
    public static void movement(Input input) {
        if (!interactive()) { reset(); return; }
        boolean shift = input.shiftKeyDown;
        if (module("ToggleSneak") instanceof ToggleSneakModule sneak && sneak.isEnabled()) {
            // Use physical hold state in Hold mode, independent of vanilla's toggle preference.
            shift = sneak.evaluateSneak(mode(sneak) == 1 && sneakHeld);
        }
        if (module("ToggleSprint") instanceof ToggleSprintModule sprint && sprint.isEnabled()) {
            boolean sprinting = sprint.evaluateSprint(shift);
            // Vanilla continues sprinting after the input is released; explicitly end our latch.
            if (sprintRequested && !sprinting) stopOwnedSprint();
            sprintRequested = sprinting;
        }
        input.shiftKeyDown = shift;
    }
    /** LocalPlayer.aiStep's keySprint.isDown() reads, after movement() ran in the same tick: the 1.21.11 Input.sprint() equivalent. */
    public static boolean sprintInput(boolean vanilla) {
        return interactive() && enabled("ToggleSprint") ? sprintRequested : vanilla;
    }
    private static void stopOwnedSprint() {
        if (sprintRequested && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.setSprinting(false);
        }
        sprintRequested = false;
    }
    /** MouseHandler.onScroll: true when Zoom used the scroll, so the hotbar does not move. */
    public static boolean scroll(double amount) {
        if (!interactive() || amount == 0) return false;
        Minecraft mc = Minecraft.getInstance();
        double notches = mc.options.discreteMouseScroll().get() ? Math.signum(amount) : amount;
        return zoom().scroll(notches * mc.options.mouseWheelSensitivity().get());
    }
    /** ZoomMixin: the FOV Minecraft computed (dynamic FOV, spyglass and fluids included) times the zoom. */
    public static double fov(double vanilla, boolean hand) {
        long now = System.nanoTime();
        double fov = vanilla * zoom().fovFactor(hand, now);
        if (!hand) { lastWorldFov = fov; lastWorldNanos = now; }
        return fov;
    }
    /** MouseHandler.turnPlayer: zoomed, the camera turns as much per on-screen pixel as unzoomed. */
    public static float zoomSensitivity() {
        return zoom().sensitivity();
    }
    public static float gamma(float vanilla) {
        // Replace only the lightmap gamma input; effects and the saved vanilla option remain native.
        double fullbright = fullbrightGamma();
        return fullbright < 0 ? vanilla : (float) Math.max(vanilla, fullbright);
    }
    /** The gamma Fullbright gives the lightmap, or -1 while it is off; FullbrightLightmapHook watches it for BadOptimizations. */
    public static double fullbrightGamma() {
        if (!(module("Fullbright") instanceof FullbrightModule fullbright) || !fullbright.isEnabled()
            || Minecraft.getInstance().level == null) return -1;
        double multiplier = fullbright.getOption("Brightness Multiplier") instanceof SliderOption value ? value.getValue() : 1;
        return fullbright.getGamma() * multiplier;
    }
}
