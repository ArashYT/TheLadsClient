package com.thelads.core.v26_2.feature;

import com.thelads.core.client.hud.HudFrameCap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * HUD FPS cap for the whole in-game HUD: vanilla, every mod HUD drawn with it, and the Lads HUD. On a due frame the HUD builds
 * as usual while GuiRenderStateCaptureMixin records what it adds to the GuiRenderState; on the frames in between that record is
 * added again instead of building, so the HUD stays on screen and updates at the cap rate.
 * <p>
 * 1.7.0: each add replays inside the Autohide opacity it was recorded in (a hidden hotbar stayed visible between builds, while its
 * items, faded when built, did not), and replayed pictures blit their last texture (Xaero's minimap drew only on build frames).
 */
public final class HudCapture {
    private record Op(float opacity, Consumer<GuiRenderState> add) {}
    private static final List<Op> OPS = new ArrayList<>();
    /** Pictures re-added by this frame's replay: their renderers blit their last texture instead of rendering again. */
    private static final Set<Object> REPLAYED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean recording;
    /** QA (Hud170Capture): pictures blitted from their last texture on replayed frames. */
    public static int replayBlits;
    /** QA (HudFlickerCapture): HUD builds and replays so far, and the time they took. */
    public static int qaBuilds, qaReplays;
    public static long qaNanos;

    private HudCapture() {}

    public static void record(Consumer<GuiRenderState> op) {
        if (recording) OPS.add(new Op(NativeAutohide.scopeOpacity, op));
    }

    /** A picture's replay, marked so its renderer blits its last texture (PictureInPictureReplayMixin, XaeroMinimapReplayMixin). */
    public static void replayPicture(GuiRenderState state, net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState picture) {
        REPLAYED.add(picture);
        state.addPicturesInPictureState(picture);
    }

    public static boolean replayed(Object picture) {
        return REPLAYED.contains(picture);
    }

    public static void extract(GuiRenderState state, Runnable build) {
        long start = System.nanoTime();
        try { extractTimed(state, build); } finally { qaNanos += System.nanoTime() - start; }
    }

    private static void extractTimed(GuiRenderState state, Runnable build) {
        REPLAYED.clear();
        var mc = Minecraft.getInstance();
        if (mc.level == null || !HudFrameCap.enabled()) {
            OPS.clear();
            HudFrameCap.reset();
            qaBuilds++;
            build.run();
            return;
        }
        var window = mc.getWindow();
        if (!HudFrameCap.due(System.nanoTime(), window.getGuiScaledWidth(), window.getGuiScaledHeight())) {
            qaReplays++;
            float scope = NativeAutohide.scopeOpacity;
            try {
                for (var op : OPS) {
                    NativeAutohide.scopeOpacity = op.opacity();
                    op.add().accept(state);
                }
            } finally {
                NativeAutohide.scopeOpacity = scope;
            }
            return;
        }
        OPS.clear();
        qaBuilds++;
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
