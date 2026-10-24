package com.thelads.core.v1_20_1.feature;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.FullbrightModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.modules.ZoomModule;
import com.thelads.core.v1_20_1.game.Gameplay1201;
import com.thelads.core.v1_20_1.game.mixin.LocalPlayerAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Client-thread state of Zoom, Toggle Sprint and Sneak and Fullbright. Never writes vanilla options and never calls
 * setSprinting(true): the Sprint and Sneak keys are read through {@link #movement} and the game's own rules decide the rest.
 */
public final class NativeFeatures1201 {
    private static Object player;
    private static boolean sprintHeld, sneakHeld, forward;
    /** The Sprint key as this player tick reads it (LocalPlayer.aiStep); null outside a tick that went through {@link #movement}. */
    private static Boolean sprintKey;
    /** QA: the world FOV of the last rendered frame, zoom included. */
    public static volatile float lastWorldFov;
    public static volatile long lastWorldNanos;
    private static double lastGamma = Double.NaN;
    private static float lastFactor = -1.0f;

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

    /** Toggle Sprint &amp; Sneak's key rows in the Lads menu: the key that toggles, and a click opens Controls. */
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

    /** Unbound, the vanilla key toggles; bound, the toggle key does and the vanilla key is a plain hold key again. */
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

    /** The window counts as focused for Lads input; QA windows are never activated, so the QA probe stands in for focus. */
    public static boolean windowActive() {
        return Minecraft.getInstance().isWindowActive() || Gameplay1201.qaActive();
    }

    public static boolean interactive() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && !mc.player.isDeadOrDying()
            && mc.screen == null && windowActive() && !mc.isPaused();
    }

    /** Toggle Sprint &amp; Sneak's toggles survive all of this: deaths, worlds, servers and restarts never clear them. */
    public static void reset(boolean newPlayer) {
        // Another player or world starts unzoomed; anything else (a screen, focus loss) zooms out smoothly.
        if (newPlayer) zoom().reset(); else zoom().release();
        com.thelads.core.v1_20_1.game.PvpView1201.release();
        sprintHeld = sneakHeld = false;
    }

    public static void reset() {
        reset(false);
    }

    private static Boolean rawApplied;

    /**
     * RawInput: the module drives GLFW's raw mouse motion (the switch Minecraft 1.20.1 has under Mouse Settings). On keeps the pointer
     * raw, off applies Windows' pointer speed and acceleration, as 26.3's module does. Applied once at startup and when the module changes.
     */
    private static void rawInput(Minecraft mc) {
        if (mc.getWindow() == null) return;
        boolean on = enabled("RawInput");
        if (rawApplied != null && rawApplied == on) return;
        rawApplied = on;
        mc.getWindow().updateRawMouseInput(on);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        rawInput(mc);
        Object currentPlayer = mc.player;
        if (currentPlayer != player) {
            reset(true);
            player = currentPlayer;
            forward = false;
            return;
        }
        if (!interactive()) {
            zoom().release();
            com.thelads.core.v1_20_1.game.PvpView1201.release();
        }
        com.thelads.core.v1_20_1.game.PvpView1201.tick(mc);
        if (mc.player != null) {
            boolean isRiding = mc.player.isPassenger() || mc.player.isFallFlying();
            Module m = module(com.thelads.core.modules.AutoPerspectiveModule.NAME);
            if (m instanceof com.thelads.core.modules.AutoPerspectiveModule apm && apm.isEnabled()) {
                if (apm.isAutoThirdPerson()) {
                    int current = mc.options.getCameraType().ordinal();
                    int target = com.thelads.core.client.AutoPerspectiveHelper.checkPerspectiveChange(isRiding, current, true);
                    if (target >= 0 && target < net.minecraft.client.CameraType.values().length) {
                        mc.options.setCameraType(net.minecraft.client.CameraType.values()[target]);
                    }
                }
                if (apm.isSmoothCamera()) {
                    mc.options.smoothCamera = isRiding;
                }
            }
        }
    }

    public static void key(int key, int scancode, int action, int modifiers) {
        if (action == GLFW.GLFW_REPEAT) return;
        if (!interactive()) {
            reset();
            return;
        }
        boolean down = (action == GLFW.GLFW_PRESS);
        if (!Gameplay1201.zoomOwnedByMod() && NativeKeyBindings1201.ZOOM.matches(key, scancode)) zoom().key(down);
        com.thelads.core.v1_20_1.game.PvpView1201.key(key, scancode, down);
        if (sprintToggleKey().matches(key, scancode)) sprintKey(down);
        if (sneakToggleKey().matches(key, scancode)) sneakKey(down);
        if (down && NativeKeyBindings1201.QUICKPLAY.matches(key, scancode)) {
            var mc = Minecraft.getInstance();
            if (mc.screen == null) mc.setScreen(new com.thelads.core.v1_20_1.gui.QuickplayScreen1201(null));
        }
        if (down && NativeKeyBindings1201.PLAY_AGAIN.matches(key, scancode)) {
            com.thelads.core.client.hypixel.quickplay.QuickplayModule.triggerPlayAgain();
        }
        if (down && NativeKeyBindings1201.ELYTRA_SWAP.matches(key, scancode)) {
            swapElytra();
        }
    }

    public static void mouse(int button, int action) {
        if (!interactive()) {
            reset();
            return;
        }
        boolean down = (action == GLFW.GLFW_PRESS);
        if (!Gameplay1201.zoomOwnedByMod() && NativeKeyBindings1201.ZOOM.matchesMouse(button)) zoom().key(down);
        com.thelads.core.v1_20_1.game.PvpView1201.mouse(button, down);
        if (sprintToggleKey().matchesMouse(button)) sprintKey(down);
        if (sneakToggleKey().matchesMouse(button)) sneakKey(down);
        if (down && NativeKeyBindings1201.QUICKPLAY.matchesMouse(button)) {
            var mc = Minecraft.getInstance();
            if (mc.screen == null) mc.setScreen(new com.thelads.core.v1_20_1.gui.QuickplayScreen1201(null));
        }
        if (down && NativeKeyBindings1201.PLAY_AGAIN.matchesMouse(button)) {
            com.thelads.core.client.hypixel.quickplay.QuickplayModule.triggerPlayAgain();
        }
        if (down && NativeKeyBindings1201.ELYTRA_SWAP.matchesMouse(button)) {
            swapElytra();
        }
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

    /**
     * KeyboardInput.tick, every player tick: the Sneak key as Toggle Sprint &amp; Sneak reads it goes into the input (vanilla's own
     * crouch, slow-down and camera follow from it), and the Sprint key's value for this tick is kept for LocalPlayer.aiStep
     * ({@link #sprintKeyDown}). aiStep then applies its own sprint rules to it, as to a held key.
     */
    public static void movement(KeyboardInput input) {
        ToggleSprintModule toggles = toggles();
        LocalPlayer self = Minecraft.getInstance().player;
        Minecraft mc = Minecraft.getInstance();
        sprintKey = null;
        if (toggles == null || self == null || mc.options == null) return;
        // What the last tick sent, and whether its input still asked to go forward (aiStep stops a sprint without it).
        toggles.sent(((LocalPlayerAccessor) self).ladsSentSprint(), forward);
        if (toggles.isDisableDoubleTap()) ((LocalPlayerAccessor) self).ladsSetSprintTriggerTime(0);
        forward = input.up && !input.down;
        boolean separateSprint = !NativeKeyBindings1201.TOGGLE_SPRINT.isUnbound();
        boolean shift = toggles.sneakInput(input.shiftKeyDown, !NativeKeyBindings1201.TOGGLE_SNEAK.isUnbound());
        boolean held = mc.options.keySprint.isDown();
        boolean sprinting = toggles.sprintInput(held, separateSprint, shift);
        // Vanilla keeps sprinting after the key is released: a toggle that ends stops it, once.
        if (toggles.sprintEnded(held, separateSprint, shift)) self.setSprinting(false);
        toggles.observe(self.isSprinting(), shift, held);
        input.shiftKeyDown = shift;
        sprintKey = sprinting;
    }

    /** LocalPlayer.aiStep's reads of the Sprint key: this tick's value from {@link #movement}, else the key itself. */
    public static boolean sprintKeyDown(KeyMapping key, boolean pressed) {
        Boolean value = sprintKey;
        return value != null && key == Minecraft.getInstance().options.keySprint ? value : pressed;
    }

    /** End of LocalPlayer.aiStep. */
    public static void movementDone() {
        sprintKey = null;
    }

    public static boolean scroll(double amount) {
        if (!interactive() || amount == 0 || Gameplay1201.zoomOwnedByMod()) return false;
        Minecraft mc = Minecraft.getInstance();
        double notches = mc.options.discreteMouseScroll().get() ? Math.signum(amount) : amount;
        return zoom().scroll(notches * mc.options.mouseWheelSensitivity().get());
    }

    /** ZoomMixin: the FOV Minecraft computed (dynamic FOV, spyglass and fluids included) times the zoom. */
    public static float fov(float vanilla, boolean hand) {
        if (hand) vanilla = com.thelads.core.modules.ViewModelModule.get().handFov(vanilla); // ViewModel's Hand FOV
        long now = System.nanoTime();
        if (Gameplay1201.zoomOwnedByMod()) {
            if (!hand) { lastWorldFov = vanilla; lastWorldNanos = now; } // QA reads the FOV even when another mod owns the zoom
            return vanilla;
        }
        float fov = vanilla * zoom().fovFactor(hand, now);
        if (!hand) {
            lastWorldFov = fov;
            lastWorldNanos = now;
        }
        return fov;
    }

    /** MouseHandler.turnPlayer: zoomed, the camera turns as much per on-screen pixel as unzoomed. */
    public static float zoomSensitivity() {
        return Gameplay1201.zoomOwnedByMod() ? 1 : zoom().sensitivity();
    }

    public static float gamma(float vanilla) {
        if (FullbrightModule.serverLimited()) vanilla = Math.min(1.0f, vanilla);
        double fullbright = fullbrightGamma();
        return fullbright < 0 ? vanilla : (float) Math.max(vanilla, fullbright);
    }

    /** The gamma Fullbright gives the lightmap (slider and multiplier), or -1 while it is off. */
    public static double fullbrightGamma() {
        Module fb = module("Fullbright");
        if (!(fb instanceof FullbrightModule fullbright) || !fullbright.isEnabled()
            || Minecraft.getInstance().level == null) return -1;
        return fullbright.effectiveGamma();
    }

    public static float fullbrightFactor() {
        Module fb = module("Fullbright");
        if (!(fb instanceof FullbrightModule fullbright) || !fullbright.isEnabled()
            || Minecraft.getInstance().level == null) return 0.0f;
        return fullbright.lightmapFactor();
    }

    /**
     * Once per frame (LightTexture.updateLightTexture's caller): whether Fullbright's gamma or factor differs from the last frame's.
     * The lightmap only rebuilds when the game's tick says so, which does not run while a menu pauses the game, so without this
     * a change showed only after the menu closed.
     */
    public static boolean gammaChanged() {
        double now = fullbrightGamma();
        float factor = fullbrightFactor();
        boolean changed = Double.compare(now, lastGamma) != 0 || Float.compare(factor, lastFactor) != 0;
        lastGamma = now;
        lastFactor = factor;
        return changed;
    }

    /**
     * Overrides the lightmap NativeImage with Fullbright's factor, lighting pitch-black areas to full daylight.
     */
    public static void overrideLightmap(com.mojang.blaze3d.platform.NativeImage pixels) {
        float factor = fullbrightFactor();
        if (factor <= 0 || pixels == null) return;
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                int pixel = pixels.getPixelRGBA(block, sky);
                int r = pixel & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = (pixel >> 16) & 0xFF;

                r = (int) (r + (255 - r) * factor);
                g = (int) (g + (255 - g) * factor);
                b = (int) (b + (255 - b) * factor);

                pixels.setPixelRGBA(block, sky, 0xFF000000 | (b << 16) | (g << 8) | r);
            }
        }
    }

    public static void swapElytra() {
        if (!com.thelads.core.client.ElytraSwap.isAllowed()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.gameMode == null) return;
        LocalPlayer player = mc.player;
        net.minecraft.world.inventory.Slot chestSlot = player.inventoryMenu.getSlot(6);
        ItemStack chestStack = chestSlot.getItem();
        boolean wearingElytra = chestStack.is(net.minecraft.world.item.Items.ELYTRA);

        int matchSlot = -1;
        for (int i = 9; i <= 44; i++) {
            net.minecraft.world.inventory.Slot s = player.inventoryMenu.getSlot(i);
            if (!s.hasItem()) continue;
            if (com.thelads.core.client.InventoryTweaks.locked(s.getContainerSlot())) continue;
            ItemStack item = s.getItem();
            if (wearingElytra) {
                net.minecraft.world.entity.EquipmentSlot equip = net.minecraft.world.entity.LivingEntity.getEquipmentSlotForItem(item);
                if (equip == net.minecraft.world.entity.EquipmentSlot.CHEST && !item.is(net.minecraft.world.item.Items.ELYTRA)) {
                    matchSlot = i;
                    break;
                }
            } else {
                if (item.is(net.minecraft.world.item.Items.ELYTRA)) {
                    matchSlot = i;
                    break;
                }
            }
        }

        if (matchSlot != -1) {
            boolean hasChest = !chestStack.isEmpty();
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, matchSlot, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, 6, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            if (hasChest) {
                mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, matchSlot, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            }
        }
    }

}
