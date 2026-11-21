package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;

/** Plays the attack sound and hurt flash at swing time; the server's duplicate sound is dropped. Visual/audio only: no packet is added, removed or re-timed; the server stays authoritative. */
public final class SwordOptimizerModule extends Module {
    public static final String NAME = "Sword Optimizer";
    public final BoolOption hitSound;
    public final BoolOption hurtFlash;

    public SwordOptimizerModule() {
        super(NAME, "Plays the melee attack sound and the target's hurt flash instantly when you hit. Server still decides damage. Audio and visual only.");
        hitSound = addOption(new BoolOption("Predict Hit Sound", true));
        hurtFlash = addOption(new BoolOption("Predict Hurt Flash", true));
        setEnabled(true);
    }
}
