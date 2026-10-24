package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public final class ConfirmDisconnectModule extends Module {
    public static final String NAME = "ConfirmDisconnect";

    public final SliderOption timeoutSeconds;

    public ConfirmDisconnectModule() {
        super(NAME, "Requires a second click to disconnect from multiplayer servers to prevent accidental disconnects.");
        timeoutSeconds = addOption(new SliderOption("Timeout (Seconds)", 3, 1, 10, 1));
        setEnabled(false);
    }

    public long getTimeoutMillis() {
        return (long) timeoutSeconds.getValue() * 1000L;
    }
}
