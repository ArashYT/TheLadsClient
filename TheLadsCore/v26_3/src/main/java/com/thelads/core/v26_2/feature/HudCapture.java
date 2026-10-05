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
 * <p>
 * Animations keep their pace: while a capped build runs, the game's frame time steps (DeltaTracker game-time and real-time delta
 * ticks) are the time since the last build (DeltaTrackerCapMixin), so anything that advances by them (Jade's fade, its health and
 * progress bars, mods' HUD animations) moves as far as it would have over the frames in between. Vanilla's spyglass zoom eases by a
 * share of the way per frame: it takes each of those frames' steps (HudScopeMixin). The Lads HUD counts the frames
 * (HudFrameCap.steps). And they look smooth: each add tells HudFrameCap what it draws and where and how (GuiRenderStateCaptureMixin);
 * while something keeps moving, resizing, turning, fading or changing colour, the HUD is built at 60 FPS or more. New content (a
 * number, a timer, a chat line) shows at the cap rate.
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
    /** The game-time step of each frame since the last build, this one included, and their game-time and real-time sums. */
    private static float[] frameSteps = new float[32];
    private static int frames, buildFrames;
    private static float gameSum, realSum, buildGame, buildReal;
    /** A capped build (the HUD or its deferred part) runs now: frame time steps are this build's sums (DeltaTrackerCapMixin). */
    private static boolean catchingUp;
    private static long buildStart;
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

    /** One add of the HUD's build, if one is being recorded (then the caller tells {@link #drew} what it draws). */
    public static boolean record(Consumer<GuiRenderState> op) {
        if (recording == null) return false;
        recording.add(new Op(NativeAutohide.scopeOpacity, op));
        return true;
    }

    /** What the add just recorded draws, and where and how (HudFrameCap.draw), in its Autohide opacity. */
    public static void drew(int what, int how) {
        HudFrameCap.draw(what, 31 * how + Float.floatToIntBits(NativeAutohide.scopeOpacity));
    }

    /** A picture's replay, marked so its renderer blits its last texture (GuiRendererReplayMixin). */
    public static void replayPicture(GuiRenderState state, PictureInPictureRenderState picture) {
        REPLAYED.add(picture);
        state.addPicturesInPictureState(picture);
    }

    public static boolean replayed(Object picture) {
        return REPLAYED.contains(picture);
    }

    /** DeltaTrackerCapMixin: the game-time step a frame's HUD code sees (the time since the last build in a capped build). */
    public static float gameStep(float frame) {
        return catchingUp ? buildGame : frame;
    }

    /** DeltaTrackerCapMixin: the real-time step a frame's HUD code sees. */
    public static float realStep(float frame) {
        return catchingUp ? buildReal : frame;
    }

    /** HudScopeMixin: the frames a capped build stands for (0 outside one) and each one's game-time step, oldest first. */
    public static int catchUpFrames() {
        return catchingUp ? buildFrames : 0;
    }

    public static float frameStep(int frame) {
        return frameSteps[frame];
    }

    /** Gui: Hud.extractRenderState. */
    public static void extract(GuiRenderState state, Runnable build) {
        long start = System.nanoTime();
        REPLAYED.clear();
        built = replaying = catchingUp = false;
        try {
            var mc = Minecraft.getInstance();
            if (mc.level == null || !HudFrameCap.enabled()) {
                HUD.clear();
                DEFERRED.clear();
                frames = 0;
                HudFrameCap.reset();
                qaBuilds++;
                build.run();
                return;
            }
            if (frames == 0) gameSum = realSum = 0; // the first frame since the last build
            if (frames == frameSteps.length) frameSteps = java.util.Arrays.copyOf(frameSteps, frames * 2);
            frameSteps[frames++] = mc.getDeltaTracker().getGameTimeDeltaTicks();
            gameSum += frameSteps[frames - 1];
            realSum += mc.getDeltaTracker().getRealtimeDeltaTicks();
            var window = mc.getWindow();
            if (!HudFrameCap.due(System.nanoTime(), window.getGuiScaledWidth(), window.getGuiScaledHeight())) {
                qaReplays++;
                replaying = true;
                replay(HUD, state);
                return;
            }
            qaBuilds++;
            buildFrames = frames;
            buildGame = gameSum;
            buildReal = realSum;
            frames = 0; // the next build counts from the next frame
            buildStart = System.nanoTime();
            HudFrameCap.wholeHud = catchingUp = true;
            try {
                build(HUD, build);
            } finally {
                HudFrameCap.wholeHud = catchingUp = false;
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
            else if (record) {
                catchingUp = true;
                build(DEFERRED, build);
                HudFrameCap.built(buildStart); // the whole build, both parts
            } else build.run();
        } finally {
            catchingUp = false;
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
