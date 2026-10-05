package com.thelads.core.v26_2.feature;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.modules.ZoomModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Input;
import com.mojang.blaze3d.platform.InputConstants;

/** Client-thread state. Never writes vanilla options or bypasses vanilla movement eligibility. */
public final class NativeFeatures {
    private static Object player;
    private static boolean sprintHeld, sneakHeld, forward;
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
    static ToggleSprintModule toggles() { return (ToggleSprintModule) module(ToggleSprintModule.NAME); }
    /** Toggle Sprint &amp; Sneak's key rows in the Lads menu: the key that toggles, and a click opens Controls. */
    public static void initialize() {
        ToggleSprintModule toggles = toggles();
        toggles.sprintKey.setLabel(() -> keyLabel(NativeKeyBindings.TOGGLE_SPRINT, "Sprint"));
        toggles.sneakKey.setLabel(() -> keyLabel(NativeKeyBindings.TOGGLE_SNEAK, "Sneak"));
        Runnable controls = () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(new KeyBindsScreen(mc.gui.screen(), mc.options));
        };
        toggles.sprintKey.setAction(controls);
        toggles.sneakKey.setAction(controls);
    }
    private static String keyLabel(KeyMapping toggle, String vanilla) {
        return toggle.isUnbound() ? "Same as " + vanilla : toggle.getTranslatedKeyMessage().getString();
    }
    /** Unbound, the vanilla key toggles; bound, the toggle key does and the vanilla key is a plain hold key again. */
    private static KeyMapping sprintToggleKey() {
        return NativeKeyBindings.TOGGLE_SPRINT.isUnbound() ? Minecraft.getInstance().options.keySprint : NativeKeyBindings.TOGGLE_SPRINT;
    }
    private static KeyMapping sneakToggleKey() {
        return NativeKeyBindings.TOGGLE_SNEAK.isUnbound() ? Minecraft.getInstance().options.keyShift : NativeKeyBindings.TOGGLE_SNEAK;
    }
    public static boolean interactive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && !mc.player.isDeadOrDying()
            && mc.gui.screen() == null && NativeWorldVerification.windowActive() && !mc.isPaused();
    }
    /** Toggle Sprint &amp; Sneak's toggles survive all of this: deaths, worlds, servers and restarts never clear them. */
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
        if (action == InputConstants.REPEAT) return;
        if (!interactive()) { reset(); return; }
        boolean down = action == InputConstants.PRESS;
        if (NativeKeyBindings.ZOOM.matches(event)) zoom().key(down);
        if (sprintToggleKey().matches(event)) sprintKey(down);
        if (sneakToggleKey().matches(event)) sneakKey(down);
    }
    public static void mouse(MouseButtonEvent event, int action) {
        if (!interactive()) { reset(); return; }
        boolean down = action == InputConstants.PRESS;
        if (NativeKeyBindings.ZOOM.matchesMouse(event)) zoom().key(down);
        if (sprintToggleKey().matchesMouse(event)) sprintKey(down);
        if (sneakToggleKey().matchesMouse(event)) sneakKey(down);
    }
    private static void sprintKey(boolean down) {
        if (down && !sprintHeld && toggles().pressSprint()) ConfigManager.save();
        sprintHeld = down;
    }
    private static void sneakKey(boolean down) {
        if (down && !sneakHeld && toggles().pressSneak()) ConfigManager.save();
        sneakHeld = down;
    }
    /**
     * KeyboardInput.tick, every player tick (screens open too, as vanilla's own toggle options): the Sprint and Sneak keys as
     * Toggle Sprint &amp; Sneak reads them. LocalPlayer.aiStep then applies its own sprint rules to them, as to held keys.
     */
    public static Input movement(Input original) {
        ToggleSprintModule toggles = toggles();
        var player = Minecraft.getInstance().player;
        // What the last tick sent, and whether its input still asked to go forward (aiStep stops a sprint without it).
        if (player != null) toggles.sent(((com.thelads.core.v26_2.mixin.LocalPlayerAccessor) player).ladsSentSprint(), forward);
        forward = original.forward() && !original.backward();
        boolean separateSprint = !NativeKeyBindings.TOGGLE_SPRINT.isUnbound();
        boolean shift = toggles.sneakInput(original.shift(), !NativeKeyBindings.TOGGLE_SNEAK.isUnbound());
        boolean sprinting = toggles.sprintInput(original.sprint(), separateSprint, shift);
        // Vanilla keeps sprinting after the key is released: a toggle that ends stops it, once.
        if (toggles.sprintEnded(original.sprint(), separateSprint, shift) && player != null) player.setSprinting(false);
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
    /** The gamma Fullbright gives the lightmap (slider and multiplier), or -1 while it is off. */
    private static double fullbrightGamma() {
        return module("Fullbright") instanceof FullbrightModule fullbright && fullbright.isEnabled() && Minecraft.getInstance().level != null
            ? fullbright.effectiveGamma() : -1;
    }
    private static double lastGamma = Double.NaN;
    /** QA only (UiCapture's control run): the lightmap is left to vanilla's tick again, as before 1.7.2. */
    static volatile boolean qaDeferGamma;
    /**
     * FullbrightMixin, once per frame: whether Fullbright's gamma (on/off or the slider) differs from the last frame's. The lightmap
     * only rebuilds when vanilla's tick says so, and that tick does not run while a menu pauses the game or when BadOptimizations
     * finds none of vanilla's inputs changed, so without this a change showed only after the menu closed.
     */
    public static boolean gammaChanged() {
        double now = fullbrightGamma();
        boolean changed = Double.compare(now, lastGamma) != 0;
        lastGamma = now;
        return changed && !qaDeferGamma;
    }
}
