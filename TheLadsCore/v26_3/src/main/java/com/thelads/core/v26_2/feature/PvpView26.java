package com.thelads.core.v26_2.feature;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.FogCustomizerModule;
import com.thelads.core.modules.FreelookModule;
import com.thelads.core.modules.TimeChangerModule;
import com.thelads.core.modules.ViewModelModule;
import com.thelads.core.modules.WeatherChangerModule;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Freelook, ViewModel, Time Changer, Weather Changer and Fog Customizer on 26.3 (the modules decide). NativeFeatures hands over the key
 * events and gameplay stops, and every client tick applies Freelook's perspective. Hooks: MouseHandlerMixin (the mouse turns
 * Freelook's camera), FreelookCameraMixin, ViewModelMixin, TimeChangerMixin, WeatherLevelMixin, WeatherPrecipitationMixin,
 * FogCustomizerMixin, NativeFeatures.fov (hand FOV).
 */
public final class PvpView26 {
    private PvpView26() {}

    public static void register() {
        ModuleSupport.registerBuiltIn(FreelookModule.NAME, ViewModelModule.NAME, TimeChangerModule.NAME, WeatherChangerModule.NAME,
            FogCustomizerModule.NAME);
        FreelookModule look = FreelookModule.get();
        look.freelookKey.setLabel(() -> label(NativeKeyBindings.FREELOOK));
        look.snaplookKey.setLabel(() -> label(NativeKeyBindings.SNAPLOOK));
        Runnable controls = () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(new KeyBindsScreen(mc.gui.screen(), mc.options));
        };
        look.freelookKey.setAction(controls);
        look.snaplookKey.setAction(controls);
    }

    private static String label(KeyMapping key) {
        return key.isUnbound() ? "Not bound" : key.getTranslatedKeyMessage().getString();
    }

    /** A key went down or up during gameplay. */
    static void key(KeyEvent event, boolean down) {
        if (NativeKeyBindings.FREELOOK.matches(event)) FreelookModule.get().key(down);
        if (NativeKeyBindings.SNAPLOOK.matches(event)) FreelookModule.get().snapKey(down);
    }

    static void mouse(MouseButtonEvent event, boolean down) {
        if (NativeKeyBindings.FREELOOK.matchesMouse(event)) FreelookModule.get().key(down);
        if (NativeKeyBindings.SNAPLOOK.matchesMouse(event)) FreelookModule.get().snapKey(down);
    }

    /** Gameplay stopped (a screen, focus loss, death, another world). */
    static void release() {
        FreelookModule.get().release();
    }

    /** Every client tick: the perspective follows the Freelook and Snaplook keys and the server rules. */
    static void tick(Minecraft mc) {
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
