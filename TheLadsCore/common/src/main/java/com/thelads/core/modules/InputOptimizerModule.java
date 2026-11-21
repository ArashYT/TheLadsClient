package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/**
 * Input Optimizer: switches for the built-in input remakes. On by default, as they ship: buffered raw mouse input (1.20.1 on
 * Windows; 26.x reads the mouse through SDL3 raw input already and 1.8.9 through its own raw mouse reader) and the second input
 * poll after the pacing wait. Both only change when the real hardware events are read, never what is sent.
 */
public final class InputOptimizerModule extends Module {
    public static final String NAME = "Input Optimizer";

    public final BoolOption bufferedMouse;
    public final BoolOption latePoll;

    public InputOptimizerModule() {
        super(NAME, "Reads mouse and keyboard input as late as possible before the game uses it: raw mouse motion in one batch, and a fresh input poll after the frame's wait.");
        bufferedMouse = addOption(new BoolOption("Buffered Raw Mouse", true));
        latePoll = addOption(new BoolOption("Late Input Poll", true));
        setEnabled(true);
    }

    public boolean bufferedMouse() { return isEnabled() && bufferedMouse.get(); }
    public boolean latePoll() { return isEnabled() && latePoll.get(); }
}
