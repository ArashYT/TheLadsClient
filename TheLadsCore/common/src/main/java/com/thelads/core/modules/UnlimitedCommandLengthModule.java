package com.thelads.core.modules;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.SliderOption;

/**
 * Unlimited command length: removes the client-side character length limit on chat input.
 * GATED: sending oversize packets can be flagged by server anticheat; blocked on unknown servers.
 */
public final class UnlimitedCommandLengthModule extends Module {
    public static final String NAME = "UnlimitedCommandLength";
    private final SliderOption maxLength;

    public UnlimitedCommandLengthModule() {
        super(NAME, "Removes vanilla chat line length restrictions for long commands. "
                + "Gated: off on servers where oversize packets are risky or rules are unknown.");
        maxLength = addOption(new SliderOption("Max Length", 2048, 256, 32767, 256));
        setEnabled(false); // Gated modules default OFF
    }

    public static boolean isAllowed() {
        Module mod = ModuleManager.getInstance().getModule(NAME);
        return mod != null && mod.isEnabled() && !HypixelSafetyManager.isDisallowed(NAME);
    }

    public static int getCommandMaxLength(int vanilla) {
        if (!isAllowed()) return vanilla;
        Module mod = ModuleManager.getInstance().getModule(NAME);
        if (mod instanceof UnlimitedCommandLengthModule ucl) {
            return ucl.maxLength.getIntValue();
        }
        return 2048;
    }
}
