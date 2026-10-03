package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ZoomModule;
import com.thelads.core.modules.ZoomTrace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Lads Zoom on 1.8.9; ZoomModule holds the state and the math, as on the other versions. The key is followed through its own
 * events (Forge's InputEvent during gameplay), not KeyBinding's pressed flag: 1.8.9 gives each key code to one binding, and
 * OptiFine's and Essential's zoom bindings sit on C too. While Lads Zoom is on, GameSettingsMixin keeps OptiFine's zoom key up
 * and EssentialZoomMixin189 Essential's zoom off. The scroll is taken in Forge's MouseEvent, before the hotbar sees it; the FOV
 * in its FOVModifier event (EntityRendererMixin says whether it is the held item's); RawMouse189 slows the mouse.
 */
public final class Zoom189 {
    public static final KeyBinding ZOOM = new KeyBinding("key.theladscore.zoom", Keyboard.KEY_C, "key.category.theladscore.controls");
    /** EntityRendererMixin: the FOV being computed is the held item's (getFOVModifier with useFOVSetting false). */
    public static boolean handPass;
    /** QA (Probe160): synthetic input stands in for window focus, and the trace logs every frame's world FOV. */
    static boolean synthetic;
    static ZoomTrace trace;
    static float lastWorldFov;
    static long lastWorldNanos;
    private static Object player;

    static ZoomModule zoom() { return (ZoomModule) ModuleManager.getInstance().getModule("Zoom"); }

    private static boolean gameplay(Minecraft mc) {
        return mc.currentScreen == null && mc.thePlayer != null && mc.theWorld != null && mc.thePlayer.getHealth() > 0
            && (Display.isActive() || synthetic);
    }

    /** Every client tick: another player starts unzoomed; a screen, focus loss or death zooms out. */
    public static void tick(Minecraft mc) {
        if (mc.thePlayer != player) {
            zoom().reset();
            player = mc.thePlayer;
        } else if (!gameplay(mc)) zoom().release();
    }

    @SubscribeEvent
    public void key(InputEvent.KeyInputEvent event) {
        // Keys LWJGL cannot name arrive as character + 256, the code KeyBinding stores for them.
        int key = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
        if (key == ZOOM.getKeyCode() && !Keyboard.isRepeatEvent() && gameplay(Minecraft.getMinecraft())) zoom().key(Keyboard.getEventKeyState());
    }

    @SubscribeEvent
    public void mouse(InputEvent.MouseInputEvent event) {
        int button = Mouse.getEventButton(); // KeyBinding stores mouse buttons as button - 100
        if (button >= 0 && button - 100 == ZOOM.getKeyCode() && gameplay(Minecraft.getMinecraft())) zoom().key(Mouse.getEventButtonState());
    }

    @SubscribeEvent
    public void wheel(MouseEvent event) {
        if (event.dwheel != 0 && gameplay(Minecraft.getMinecraft()) && zoom().scroll(Math.signum(event.dwheel))) event.setCanceled(true);
    }

    @SubscribeEvent
    public void fov(EntityViewRenderEvent.FOVModifier event) {
        long now = System.nanoTime();
        float fov = event.getFOV() * zoom().fovFactor(handPass, now);
        event.setFOV(fov);
        if (!handPass) {
            lastWorldFov = fov;
            lastWorldNanos = now;
        }
    }

    /** RenderTickEvent END: one entry of QA's per-frame FOV log. */
    public static void frame() {
        if (trace != null) trace.frame(lastWorldFov, lastWorldNanos);
    }

    public static float sensitivity() {
        return zoom().sensitivity();
    }

    /** GameSettingsMixin: OptiFine reads its zoom key through GameSettings.isKeyDown; it stays up while Lads Zoom is on. */
    public static boolean blocksOptiFine(KeyBinding key) {
        return key != null && "of.key.zoom".equals(key.getKeyDescription()) && zoom().isEnabled();
    }
}
