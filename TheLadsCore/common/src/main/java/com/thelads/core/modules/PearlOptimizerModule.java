package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/** Shows a client-only ender pearl the moment you throw, replaced by the real one when the server's arrives. Visual/audio only: no packet is added, removed or re-timed; the server stays authoritative. */
public final class PearlOptimizerModule extends Module {
    public static final String NAME = "Pearl Optimizer";

    public PearlOptimizerModule() {
        super(NAME, "Shows your thrown ender pearl instantly; it is replaced by the real server pearl. Never moves you. Visual only.");
        setEnabled(true);
    }
}
