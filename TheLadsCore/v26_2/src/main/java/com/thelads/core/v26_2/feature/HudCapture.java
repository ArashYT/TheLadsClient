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
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/**
 * HUD FPS cap for the whole in-game HUD: vanilla, every mod HUD drawn with it, and the Lads HUD. On a due frame the HUD builds
 * as usual while GuiRenderStateCaptureMixin records what it adds to the GuiRenderState; on the frames in between that record is
 * added again instead of building, so the HUD stays on screen and updates at the cap rate.
 * <p>
 * The HUD is built in two places of the GUI (GuiHudCapMixin): Hud.extractRenderState, and what it defers to the end of the GUI,
 * after screens, toasts and F3 (Hud.extractDeferredSubtitles): the subtitles and Fabric's last HUD layer, where
 * HudElementRegistry.addLast puts mods' HUDs (Jade, Item Physics' throw bar, Flashback's replay overlay, Architectury's and Puzzles
 * Lib's HUD events). Each is recorded and replayed in its own place; before 1.7.2 the deferred one was drawn on build frames only,
 * so it flickered. The rest of the GUI (screens, toasts, F3, the saving indicator, mods' own GUI hooks) is drawn every frame.
 * <p>
 * 1.7.0: each add replays inside the Autohide opacity it was recorded in (a hidden hotbar stayed visible between builds, while its
 * items, faded when built, did not). A replayed picture blits the texture its renderer last showed it with (GuiRendererReplayMixin).
 */
public final class HudCapture {
    private record Op(float opacity, Consumer<GuiRenderState> add) {}
    private static final List<Op> HUD = new ArrayList<>(), DEFERRED = new ArrayList<>();
    /** Pictures re-added by this frame's replay: their renderers blit their last texture instead of rendering again. */
    private static final Set<Object> REPLAYED = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Where GuiRenderState adds go now; null while nothing is recorded. */
    private static List<Op> recording;
    /** This frame's HUD was built and recorded, or replayed: its deferred part does the same. */
    private static boolean built, replaying;
    /** QA (Hud170Capture): pictures blitted from their last texture on replayed frames. */
    public static int replayBlits;
    /** QA (HudFlickerCapture): HUD builds and replays so far, and the time they took. */
    public static int qaBuilds, qaReplays;
    public static long qaNanos;

    private HudCapture() {}

    /** A picture renderer (PictureInPictureReplayMixin): blits its texture again if the state it last showed is this one. */
    public interface ReplayablePicture {
        boolean lads$blitAgain(PictureInPictureRenderState state, GuiRenderState gui);
    }

    public static void record(Consumer<GuiRenderState> op) {
        if (recording != null) recording.add(new Op(NativeAutohide.scopeOpacity, op));
    }

    /** A picture's replay, marked so its renderer blits its last texture (GuiRendererReplayMixin). */
    public static void replayPicture(GuiRenderState state, PictureInPictureRenderState picture) {
        REPLAYED.add(picture);
        state.addPicturesInPictureState(picture);
    }

    public static boolean replayed(Object picture) {
        return REPLAYED.contains(picture);
    }

    /** Gui: Hud.extractRenderState. */
    public static void extract(GuiRenderState state, Runnable build) {
        long start = System.nanoTime();
        REPLAYED.clear();
        built = replaying = false;
        try {
            var mc = Minecraft.getInstance();
            if (mc.level == null || !HudFrameCap.enabled()) {
                HUD.clear();
                DEFERRED.clear();
                HudFrameCap.reset();
                qaBuilds++;
                build.run();
                return;
            }
            var window = mc.getWindow();
            if (!HudFrameCap.due(System.nanoTime(), window.getGuiScaledWidth(), window.getGuiScaledHeight())) {
                qaReplays++;
                replaying = true;
                replay(HUD, state);
                return;
            }
            qaBuilds++;
            HudFrameCap.wholeHud = true;
            try {
                build(HUD, build);
            } finally {
                HudFrameCap.wholeHud = false;
            }
            built = true;
        } finally {
            qaNanos += System.nanoTime() - start;
        }
    }

    /** Gui: Hud.extractDeferredSubtitles, built or replayed as this frame's HUD was (as usual when the HUD was neither). */
    public static void extractDeferred(GuiRenderState state, Runnable build) {
        long start = System.nanoTime();
        boolean replay = replaying, record = built;
        built = replaying = false;
        try {
            if (replay) replay(DEFERRED, state);
            else if (record) build(DEFERRED, build);
            else build.run();
        } finally {
            qaNanos += System.nanoTime() - start;
        }
    }

    private static void build(List<Op> ops, Runnable build) {
        ops.clear();
        recording = ops;
        try {
            build.run();
        } catch (Throwable t) {
            HUD.clear(); // never replay a partial build
            DEFERRED.clear();
            HudFrameCap.reset();
            throw t;
        } finally {
            recording = null;
        }
    }

    private static void replay(List<Op> ops, GuiRenderState state) {
        float scope = NativeAutohide.scopeOpacity;
        try {
            for (var op : ops) {
                NativeAutohide.scopeOpacity = op.opacity();
                op.add().accept(state);
            }
        } finally {
            NativeAutohide.scopeOpacity = scope;
        }
    }
}
