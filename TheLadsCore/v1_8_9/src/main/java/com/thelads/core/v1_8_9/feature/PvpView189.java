package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.FogCustomizerModule;
import com.thelads.core.modules.FreelookModule;
import com.thelads.core.modules.TimeChangerModule;
import com.thelads.core.modules.ViewModelModule;
import com.thelads.core.modules.WeatherChangerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * Freelook, ViewModel, Time Changer, Weather Changer and Fog Customizer on 1.8.9 (the modules decide). The Freelook and Snaplook keys
 * are followed through their own events like Zoom189 (1.8.9 gives each key code to one binding); every tick applies the perspective.
 * Hooks: FreelookCameraMixin189 (mouse and orientCamera), FreelookRenderInfoMixin189 and FreelookRenderManagerMixin189 (particles and
 * name tags face the camera), ViewModelMixin189, WorldTimeWeatherMixin189, EntityRendererFogOfMixin189 (fog and snow), Zoom189 (hand FOV).
 */
public final class PvpView189 {
    public static final KeyBinding FREELOOK = new KeyBinding("key.theladscore.freelook", Keyboard.KEY_NONE, "key.category.theladscore.controls");
    public static final KeyBinding SNAPLOOK = new KeyBinding("key.theladscore.snaplook", Keyboard.KEY_NONE, "key.category.theladscore.controls");

    public static void register() {
        ClientRegistry.registerKeyBinding(FREELOOK);
        ClientRegistry.registerKeyBinding(SNAPLOOK);
        MinecraftForge.EVENT_BUS.register(new PvpView189());
        ModuleSupport.registerBuiltIn(FreelookModule.NAME, ViewModelModule.NAME, TimeChangerModule.NAME, WeatherChangerModule.NAME,
            FogCustomizerModule.NAME);
        FreelookModule look = FreelookModule.get();
        look.freelookKey.setLabel(() -> label(FREELOOK));
        look.snaplookKey.setLabel(() -> label(SNAPLOOK));
        Runnable controls = () -> Minecraft.getMinecraft().displayGuiScreen(new GuiControls(Minecraft.getMinecraft().currentScreen, Minecraft.getMinecraft().gameSettings));
        look.freelookKey.setAction(controls);
        look.snaplookKey.setAction(controls);
    }

    private static String label(KeyBinding key) {
        return key.getKeyCode() == 0 ? "Not bound" : GameSettings.getKeyDisplayString(key.getKeyCode());
    }

    private static boolean gameplay(Minecraft mc) {
        return mc.currentScreen == null && mc.thePlayer != null && mc.theWorld != null && mc.thePlayer.getHealth() > 0 && Display.isActive();
    }

    @SubscribeEvent
    public void key(InputEvent.KeyInputEvent event) {
        if (Keyboard.isRepeatEvent()) return;
        // Keys LWJGL cannot name arrive as character + 256, the code KeyBinding stores for them.
        press(Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey(), Keyboard.getEventKeyState());
    }

    @SubscribeEvent
    public void mouse(InputEvent.MouseInputEvent event) {
        if (Mouse.getEventButton() >= 0) press(Mouse.getEventButton() - 100, Mouse.getEventButtonState()); // KeyBinding stores button - 100
    }

    private static void press(int code, boolean down) {
        if (code == 0 || !gameplay(Minecraft.getMinecraft())) return;
        if (code == FREELOOK.getKeyCode()) FreelookModule.get().key(down);
        if (code == SNAPLOOK.getKeyCode()) FreelookModule.get().snapKey(down);
    }

    /** Every client tick: a screen, focus loss or death ends both views; the perspective follows the keys and the server rules. */
    public static void tick(Minecraft mc) {
        FreelookModule look = FreelookModule.get();
        if (!gameplay(mc)) look.release();
        if (mc.thePlayer == null) return;
        int now = mc.gameSettings.thirdPersonView;
        int next = look.perspective(now, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        if (next != now) {
            mc.gameSettings.thirdPersonView = next;
            mc.renderGlobal.setDisplayListEntitiesDirty();
        }
    }

    /** FreelookCameraMixin189: the mouse turned Freelook's camera; 1.8.9 rebuilds its visible chunks only when the player turns. */
    public static boolean turn(float yaw, float pitch) {
        if (!FreelookModule.get().turn(yaw * 0.15, -pitch * 0.15)) return false; // Entity.setAngles' scale; its pitch goes up
        Minecraft.getMinecraft().renderGlobal.setDisplayListEntitiesDirty();
        return true;
    }
}
