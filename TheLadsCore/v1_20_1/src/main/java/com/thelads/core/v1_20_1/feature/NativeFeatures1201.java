package com.thelads.core.v1_20_1.feature;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.modules.ZoomModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import org.lwjgl.glfw.GLFW;

public final class NativeFeatures1201 {
    private static Object player;
    private static boolean sprintHeld;
    private static boolean sneakHeld;

    private NativeFeatures1201() {}

    public static Module module(String name) {
        return ModuleManager.getInstance().getModule(name);
    }

    public static boolean enabled(String name) {
        Module m = module(name);
        return m != null && m.isEnabled();
    }

    public static ZoomModule zoom() {
        return (ZoomModule) module("Zoom");
    }

    public static ToggleSprintModule toggles() {
        return (ToggleSprintModule) module(ToggleSprintModule.NAME);
    }

    public static void initialize() {
        ToggleSprintModule toggles = toggles();
        if (toggles == null) return;
        toggles.sprintKey.setLabel(() -> keyLabel(NativeKeyBindings1201.TOGGLE_SPRINT, "Sprint"));
        toggles.sneakKey.setLabel(() -> keyLabel(NativeKeyBindings1201.TOGGLE_SNEAK, "Sneak"));
        Runnable controls = () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new KeyBindsScreen(mc.screen, mc.options));
        };
        toggles.sprintKey.setAction(controls);
        toggles.sneakKey.setAction(controls);
    }

    private static String keyLabel(KeyMapping toggle, String vanilla) {
        return toggle.isUnbound() ? "Same as " + vanilla : toggle.getTranslatedKeyMessage().getString();
    }

    private static KeyMapping sprintToggleKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return NativeKeyBindings1201.TOGGLE_SPRINT;
        return NativeKeyBindings1201.TOGGLE_SPRINT.isUnbound() ? mc.options.keySprint : NativeKeyBindings1201.TOGGLE_SPRINT;
    }

    private static KeyMapping sneakToggleKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return NativeKeyBindings1201.TOGGLE_SNEAK;
        return NativeKeyBindings1201.TOGGLE_SNEAK.isUnbound() ? mc.options.keyShift : NativeKeyBindings1201.TOGGLE_SNEAK;
    }

    public static boolean interactive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && !mc.player.isDeadOrDying()
            && mc.screen == null && mc.isWindowActive() && !mc.isPaused();
    }

    public static void reset(boolean newPlayer) {
        if (newPlayer) zoom().reset(); else zoom().release();
        sprintHeld = sneakHeld = false;
    }

    public static void reset() {
        reset(false);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Object currentPlayer = mc.player;
        if (currentPlayer != player) {
            reset(true);
            player = currentPlayer;
            return;
        }
        if (!interactive()) {
            zoom().release();
        }
        if (mc.player != null && toggles() != null) {
            boolean keySprintDown = mc.options != null && mc.options.keySprint.isDown();
            toggles().observe(mc.player.isSprinting(), mc.player.isShiftKeyDown(), keySprintDown);
        }
    }

    public static void key(int key, int scancode, int action, int modifiers) {
        if (action == GLFW.GLFW_REPEAT) return;
        if (!interactive()) {
            reset();
            return;
        }
        boolean down = (action == GLFW.GLFW_PRESS);
        if (NativeKeyBindings1201.ZOOM.matches(key, scancode)) zoom().key(down);
        if (sprintToggleKey().matches(key, scancode)) sprintKey(down);
        if (sneakToggleKey().matches(key, scancode)) sneakKey(down);
    }

    public static void mouse(int button, int action) {
        if (!interactive()) {
            reset();
            return;
        }
        boolean down = (action == GLFW.GLFW_PRESS);
        if (NativeKeyBindings1201.ZOOM.matchesMouse(button)) zoom().key(down);
        if (sprintToggleKey().matchesMouse(button)) sprintKey(down);
        if (sneakToggleKey().matchesMouse(button)) sneakKey(down);
    }

    private static void sprintKey(boolean down) {
        if (down && !sprintHeld) {
            ToggleSprintModule t = toggles();
            if (t != null && t.pressSprint()) ConfigManager.save();
        }
        sprintHeld = down;
    }

    private static void sneakKey(boolean down) {
        if (down && !sneakHeld) {
            ToggleSprintModule t = toggles();
            if (t != null && t.pressSneak()) ConfigManager.save();
        }
        sneakHeld = down;
    }

    public static boolean isSprintToggled() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ToggleSprintModule toggles = toggles();
        if (toggles == null || !toggles.isEnabled()) return false;
        boolean separateSprint = !NativeKeyBindings1201.TOGGLE_SPRINT.isUnbound();
        boolean separateSneak = !NativeKeyBindings1201.TOGGLE_SNEAK.isUnbound();
        boolean shiftDown = mc.options != null && mc.options.keyShift.isDown();
        boolean sneaking = toggles.sneakInput(shiftDown, separateSneak) || mc.player.isShiftKeyDown();
        boolean sprintDown = mc.options != null && mc.options.keySprint.isDown();
        return toggles.sprintInput(sprintDown, separateSprint, sneaking);
    }

    public static boolean isSneakToggled() {
        ToggleSprintModule toggles = toggles();
        if (toggles == null || !toggles.isEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean separateSneak = !NativeKeyBindings1201.TOGGLE_SNEAK.isUnbound();
        boolean shiftDown = mc.options != null && mc.options.keyShift.isDown();
        return toggles.sneakInput(shiftDown, separateSneak);
    }

    public static boolean scroll(double amount) {
        if (!interactive() || amount == 0) return false;
        Minecraft mc = Minecraft.getInstance();
        double notches = mc.options.discreteMouseScroll().get() ? Math.signum(amount) : amount;
        return zoom().scroll(notches * mc.options.mouseWheelSensitivity().get());
    }

    public static float fov(float vanilla, boolean hand) {
        long now = System.nanoTime();
        return vanilla * zoom().fovFactor(hand, now);
    }

    public static float zoomSensitivity() {
        return zoom().sensitivity();
    }

    public static float gamma(float vanilla) {
        double fullbright = fullbrightGamma();
        return fullbright < 0 ? vanilla : (float) Math.max(vanilla, fullbright);
    }

    public static double fullbrightGamma() {
        Module fb = module("Fullbright");
        if (!(fb instanceof FullbrightModule fullbright) || !fullbright.isEnabled()
            || Minecraft.getInstance().level == null) return -1;
        return fullbright.effectiveGamma();
    }
}
