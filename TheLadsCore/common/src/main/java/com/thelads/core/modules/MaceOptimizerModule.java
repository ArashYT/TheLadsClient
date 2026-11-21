package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/** Plays the mace smash sound and particles the moment you swing, instead of after the server round trip. Visual/audio only: no packet is added, removed or re-timed; the server stays authoritative. */
public final class MaceOptimizerModule extends Module {
    public static final String NAME = "Mace Optimizer";

    public MaceOptimizerModule() {
        super(NAME, "Plays the mace smash sound and particles instantly on a smash attack; the server's duplicate is dropped. Visual and audio only.");
        setEnabled(true);
    }
}
