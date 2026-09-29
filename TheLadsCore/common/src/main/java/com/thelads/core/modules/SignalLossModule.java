package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.TextOption;

/** Complete SignalLoss preferences. Whole-millisecond text fields preserve the upstream int range. */
public final class SignalLossModule extends Module {
    public final TextOption timeout = addOption(new TextOption("Timeout Threshold (ms)", "2000"));
    public final TextOption minimum = addOption(new TextOption("Minimum Warning Time (ms)", "2000"));
    public final TextOption linger = addOption(new TextOption("Recovery Linger Time (ms)", "1000"));
    public final BoolOption background = addOption(new BoolOption("Draw Background", true));
    public final BoolOption singleplayer = addOption(new BoolOption("Show in Singleplayer", false));
    public final DropdownOption position = addOption(new DropdownOption("Position", 1, "Left", "Center", "Right"));
    public final ColorOption textColor = addOption(new ColorOption("Text Color", false, 0xffff5555));
    public final ColorOption backgroundColor = addOption(new ColorOption("Background Color", false, 0xa0000000));
    public SignalLossModule() {
        super("SignalLoss", "Warn when received server packets stop. Timing fields use whole milliseconds (0–2147483647); invalid text uses the default.");
        setEnabled(true);
    }
    public static int milliseconds(TextOption option, int fallback) {
        try { int value = Integer.parseInt(option.getValue().trim()); return value >= 0 ? value : fallback; }
        catch (NumberFormatException invalid) { return fallback; }
    }
}
