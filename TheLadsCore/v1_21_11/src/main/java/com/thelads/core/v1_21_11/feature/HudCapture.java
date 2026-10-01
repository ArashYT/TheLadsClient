package com.thelads.core.v1_21_11.feature;

import com.thelads.core.client.hud.HudFrameCap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.GuiRenderState;

/**
 * HUD FPS cap for the whole in-game HUD: vanilla, every mod HUD drawn with it, and the Lads HUD. On a due frame the HUD builds
 * as usual while GuiRenderStateCaptureMixin records what it adds to the GuiRenderState; on the frames in between that record is
 * added again instead of building, so the HUD stays on screen and updates at the cap rate.
 */
public final class HudCapture {
    private static final List<Consumer<GuiRenderState>> OPS = new ArrayList<>();
    private static boolean recording;

    private HudCapture() {}

    public static void record(Consumer<GuiRenderState> op) {
        if (recording) OPS.add(op);
    }

    public static void extract(GuiRenderState state, Runnable build) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !HudFrameCap.enabled()) {
            OPS.clear();
            HudFrameCap.reset();
            build.run();
            return;
        }
        var window = mc.getWindow();
        if (!HudFrameCap.due(System.nanoTime(), window.getGuiScaledWidth(), window.getGuiScaledHeight())) {
            for (var op : OPS) op.accept(state);
            return;
        }
        OPS.clear();
        recording = HudFrameCap.wholeHud = true;
        try {
            build.run();
        } catch (Throwable t) {
            OPS.clear(); // never replay a partial build
            HudFrameCap.reset();
            throw t;
        } finally {
            recording = HudFrameCap.wholeHud = false;
        }
    }
}
