package com.thelads.core.v1_21_11.feature;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.modules.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Input;
import org.lwjgl.glfw.GLFW;

/** Client-thread state. Never writes vanilla options or bypasses vanilla movement eligibility. */
public final class NativeFeatures {
    private static Object player;
    private static boolean sprintHeld, sneakHeld;
    /** QA: the world FOV of the last rendered frame, zoom included, and when Zoom computed it. */
    static float lastWorldFov;
    static long lastWorldNanos;

    private NativeFeatures() {}

    private static Module module(String name) { return ModuleManager.getInstance().getModule(name); }
    private static ZoomModule zoom() { return (ZoomModule) module("Zoom"); }
    public static boolean enabled(String name) {
        Module module = module(name);
        return module != null && module.isEnabled();
    }
    private static ToggleSprintModule toggles() { return (ToggleSprintModule) module(ToggleSprintModule.NAME); }
    public static boolean interactive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && !mc.player.isDeadOrDying()
            && mc.screen == null && NativeWorldVerification.windowActive() && !mc.isPaused();
    }
    /** 1.21.11 is frozen: Toggle Sprint &amp; Sneak toggles with the Sprint and Sneak keys only; toggles are never cleared. */
    public static void reset(boolean newPlayer) {
        // Another player or world starts unzoomed; anything else (a screen, focus loss) zooms out smoothly.
        if (newPlayer) zoom().reset(); else zoom().release();
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
        if (!interactive()) zoom().release();
    }
    public static void key(KeyEvent event, int action) {
        if (action == GLFW.GLFW_REPEAT) return;
        if (!interactive()) { reset(); return; }
        boolean down = action == GLFW.GLFW_PRESS;
        Minecraft mc = Minecraft.getInstance();
        if (NativeKeyBindings.ZOOM.matches(event)) zoom().key(down);
        if (mc.options.keySprint.matches(event)) sprintKey(down);
        if (mc.options.keyShift.matches(event)) sneakKey(down);
    }
    public static void mouse(MouseButtonEvent event, int action) {
        if (!interactive()) { reset(); return; }
        boolean down = action == GLFW.GLFW_PRESS;
        Minecraft mc = Minecraft.getInstance();
        if (NativeKeyBindings.ZOOM.matchesMouse(event)) zoom().key(down);
        if (mc.options.keySprint.matchesMouse(event)) sprintKey(down);
        if (mc.options.keyShift.matchesMouse(event)) sneakKey(down);
    }
    private static void sprintKey(boolean down) {
        if (down && !sprintHeld && toggles().pressSprint()) ConfigManager.save();
        sprintHeld = down;
    }
    private static void sneakKey(boolean down) {
        if (down && !sneakHeld && toggles().pressSneak()) ConfigManager.save();
        sneakHeld = down;
    }
    public static Input movement(Input original) {
        ToggleSprintModule toggles = toggles();
        boolean shift = toggles.sneakInput(original.shift(), false);
        boolean sprinting = toggles.sprintInput(original.sprint(), false, shift);
        var player = Minecraft.getInstance().player;
        // Vanilla keeps sprinting after the key is released: a toggle that ends stops it, once.
        if (toggles.sprintEnded(original.sprint(), false, shift) && player != null) player.setSprinting(false);
        toggles.observe(player != null && player.isSprinting(), shift, original.sprint());
        if (shift == original.shift() && sprinting == original.sprint()) return original;
        return new Input(original.forward(), original.backward(), original.left(), original.right(),
            original.jump(), shift, sprinting);
    }
    /** MouseHandler.onScroll: true when Zoom used the scroll, so the hotbar does not move. */
    public static boolean scroll(double amount) {
        if (!interactive() || amount == 0) return false;
        Minecraft mc = Minecraft.getInstance();
        double notches = mc.options.discreteMouseScroll().get() ? Math.signum(amount) : amount;
        return zoom().scroll(notches * mc.options.mouseWheelSensitivity().get());
    }
    /** ZoomMixin: the FOV Minecraft computed (dynamic FOV, spyglass and fluids included) times the zoom. */
    public static float fov(float vanilla, boolean hand) {
        long now = System.nanoTime();
        float fov = vanilla * zoom().fovFactor(hand, now);
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
        return fullbright.effectiveGamma();
    }
}
