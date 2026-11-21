package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;
import com.thelads.core.perf.LowLatencyTuner;

/**
 * Low Latency: the tuning of the built-in input-latency pacing (FramePacer, the Reflex-style frame queue limiter). The pacing
 * itself is always on; this module, off by default because like the original it can cost frame rate, adds the user's wait offset,
 * the frame-rate back-off with its smoothing and cap (LowLatencyTuner) and the debug overlay. It only delays the render thread's
 * own frame loop, never network packets.
 */
public final class LowLatencyModule extends Module {
    public static final String NAME = "Low Latency";

    public final SliderOption waitOffset;
    public final BoolOption debugOverlay;
    private final LowLatencyTuner tuner = new LowLatencyTuner();

    public LowLatencyModule() {
        super(NAME, "Waits just before each frame reads input when the GPU is the bottleneck, so the frames the driver queues do not make your clicks and turns show up late. Can cost a few frames per second.");
        waitOffset = addOption(new SliderOption("Wait Offset", 0, -2, 5, 0.1).suffix(" ms"));
        debugOverlay = addOption(new BoolOption("Debug Overlay", false));
        setEnabled(false);
    }

    /** The tuner the pacer uses this frame (offset refreshed), or null while the module is off. Render thread. */
    public LowLatencyTuner activeTuner() {
        if (!isEnabled()) return null;
        tuner.offsetMs(waitOffset.getValue());
        return tuner;
    }

    /** Lines for the debug screen: empty while the module or its overlay is off. */
    public java.util.List<String> overlayLines() {
        return isEnabled() && debugOverlay.get() ? tuner.debugLines() : java.util.Collections.<String>emptyList();
    }
}
