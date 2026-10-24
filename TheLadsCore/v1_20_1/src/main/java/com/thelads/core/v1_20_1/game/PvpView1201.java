package com.thelads.core.v1_20_1.game;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.FogCustomizerModule;
import com.thelads.core.modules.FreelookModule;
import com.thelads.core.modules.TimeChangerModule;
import com.thelads.core.modules.ViewModelModule;
import com.thelads.core.modules.WeatherChangerModule;
import com.thelads.core.v1_20_1.feature.NativeKeyBindings1201;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;

/**
 * Freelook, ViewModel, Time Changer, Weather Changer and Fog Customizer on 1.20.1 (the modules decide). NativeFeatures1201 hands over
 * the key events and gameplay stops, and every client tick applies Freelook's perspective. Hooks: MouseHandlerMixin (the mouse turns
 * Freelook's camera), FreelookCameraMixin, ViewModelMixin, TimeChangerMixin, WeatherLevelMixin, WeatherSnowMixin, FogCustomizerMixin,
 * NativeFeatures1201.fov (hand FOV).
 */
public final class PvpView1201 {
    private PvpView1201() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(FreelookModule.NAME, ViewModelModule.NAME, TimeChangerModule.NAME, WeatherChangerModule.NAME,
            FogCustomizerModule.NAME);
        FreelookModule look = FreelookModule.get();
        look.freelookKey.setLabel(() -> label(NativeKeyBindings1201.FREELOOK));
        look.snaplookKey.setLabel(() -> label(NativeKeyBindings1201.SNAPLOOK));
        Runnable controls = () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new KeyBindsScreen(mc.screen, mc.options));
        };
        look.freelookKey.setAction(controls);
        look.snaplookKey.setAction(controls);
    }

    private static String label(KeyMapping key) {
        return key.isUnbound() ? "Not bound" : key.getTranslatedKeyMessage().getString();
    }

    /** A key went down or up during gameplay. */
    public static void key(int key, int scancode, boolean down) {
        if (NativeKeyBindings1201.FREELOOK.matches(key, scancode)) FreelookModule.get().key(down);
        if (NativeKeyBindings1201.SNAPLOOK.matches(key, scancode)) FreelookModule.get().snapKey(down);
    }

    public static void mouse(int button, boolean down) {
        if (NativeKeyBindings1201.FREELOOK.matchesMouse(button)) FreelookModule.get().key(down);
        if (NativeKeyBindings1201.SNAPLOOK.matchesMouse(button)) FreelookModule.get().snapKey(down);
    }

    /** Gameplay stopped (a screen, focus loss, death, another world). */
    public static void release() {
        FreelookModule.get().release();
    }

    /** Every client tick: the perspective follows the Freelook and Snaplook keys and the server rules. */
    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        CameraType now = mc.options.getCameraType();
        int next = FreelookModule.get().perspective(now.ordinal(), mc.player.getYRot(), mc.player.getXRot());
        if (next != now.ordinal()) mc.options.setCameraType(next == FreelookModule.FRONT ? CameraType.THIRD_PERSON_FRONT
            : next == FreelookModule.BACK ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
    }

    /** MouseHandler.turnPlayer: true when Freelook's camera took the turn (Entity.turn's 0.15 degrees per unit). */
    public static boolean turn(double yaw, double pitch) {
        return FreelookModule.get().turn(yaw * 0.15, pitch * 0.15);
    }
}
