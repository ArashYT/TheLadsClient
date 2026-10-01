package com.thelads.core.v1_21_1.feature;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.modules.ToggleSneakModule;
import com.thelads.core.modules.ToggleSprintModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import org.lwjgl.glfw.GLFW;

/** Client-thread state. Never writes vanilla options or bypasses vanilla movement eligibility. Same rules as 1.21.11 and 26.x. */
public final class NativeFeatures {
    private static Object player;
    private static boolean zoomHeld, sprintHeld, sneakHeld, sprintRequested;
    private static float zoomPrevious = 1, zoomCurrent = 1, zoomTarget = .25f;

    private NativeFeatures() {}

    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }
    public static boolean enabled(String name) {
        Module module = module(name);
        return module != null && module.isEnabled();
    }
    private static boolean option(Module module, String name, boolean fallback) {
        return module.getOption(name) instanceof BoolOption value ? value.get() : fallback;
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
        zoomHeld = sprintHeld = sneakHeld = false;
        zoomPrevious = zoomCurrent = 1;
        zoomTarget = .25f;
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
            zoomHeld = false;
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
        Module zoom = module("Zoom");
        if (zoom == null || !zoom.isEnabled()) {
            zoomHeld = false;
            zoomPrevious = zoomCurrent = 1;
            zoomTarget = .25f;
            return;
        }
        zoomPrevious = zoomCurrent;
        float target = zoomHeld ? zoomTarget : 1;
        zoomCurrent = option(zoom, "Smooth Zoom", true)
            ? zoomCurrent + .3f * (target - zoomCurrent) : target;
        if (!zoomHeld) zoomTarget = .25f;
    }
    public static void key(int key, int scancode, int action) {
        if (action == GLFW.GLFW_REPEAT) return;
        if (!interactive()) { reset(); return; }
        boolean down = action == GLFW.GLFW_PRESS;
        Minecraft mc = Minecraft.getInstance();
        if (NativeKeyBindings.ZOOM.matches(key, scancode)) zoomHeld = down && enabled("Zoom");
        if (mc.options.keySprint.matches(key, scancode)) sprintKey(down);
        if (mc.options.keyShift.matches(key, scancode)) sneakKey(down);
    }
    public static void mouse(int button, int action) {
        if (!interactive()) { reset(); return; }
        boolean down = action == GLFW.GLFW_PRESS;
        Minecraft mc = Minecraft.getInstance();
        if (NativeKeyBindings.ZOOM.matchesMouse(button)) zoomHeld = down && enabled("Zoom");
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
    public static boolean scroll(double amount) {
        Module zoom = module("Zoom");
        if (!interactive() || !zoomHeld || zoom == null || !zoom.isEnabled()
            || !option(zoom, "Scroll to Zoom", true) || amount == 0) return false;
        Minecraft mc = Minecraft.getInstance();
        double adjusted = mc.options.discreteMouseScroll().get() ? Math.signum(amount) : amount;
        adjusted *= mc.options.mouseWheelSensitivity().get();
        zoomTarget = (float) Math.max(.05, Math.min(.8, zoomTarget - adjusted * .05));
        return true;
    }
    public static float zoom(float partialTick, boolean hand) {
        Module zoom = module("Zoom");
        if (!interactive() || zoom == null || !zoom.isEnabled()
            || (hand && !option(zoom, "Hand Zoom", true))) return 1;
        if (!option(zoom, "Smooth Zoom", true)) return zoomHeld ? zoomTarget : 1;
        float delta = Math.max(0, Math.min(1, partialTick));
        return zoomPrevious + delta * (zoomCurrent - zoomPrevious);
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
