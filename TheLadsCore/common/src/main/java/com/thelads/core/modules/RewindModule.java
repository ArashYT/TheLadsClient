package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * Rewind: instant clip buffering and background replay recording (parity with Lunar Client Rewind).
 */
public class RewindModule extends Module {
    public static final String NAME = "Rewind";

    public final SliderOption bufferDuration;
    public final BoolOption notifyOnClip;
    public final BoolOption recordMic;

    public RewindModule() {
        super(NAME, "Built-in instant clipping buffer and shadow rewind recorder.");
        bufferDuration = addOption(new SliderOption("Buffer duration (s)", 30, 10, 120, 5));
        notifyOnClip = addOption(new BoolOption("Notification on clip", true));
        recordMic = addOption(new BoolOption("Record microphone", false));
        setEnabled(false);
    }
}
