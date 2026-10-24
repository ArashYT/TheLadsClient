package com.thelads.core.modules;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

/**
 * MLG Helper: calculates and highlights landing position to clutch fatal falls with cobweb, water bucket, or slime block (parity with Badlion).
 */
public class MlgCobwebModule extends Module {
    public static final String NAME = "MLG Helper";

    public final BoolOption waterBucket;
    public final BoolOption cobweb;
    public final BoolOption slimeBlock;
    public final SliderOption minFallDistance;

    public MlgCobwebModule() {
        super(NAME, "Provides helper indicators for positioning cobwebs, water buckets or slime to break fatal falls.");
        waterBucket = addOption(new BoolOption("Water bucket clutch", true));
        cobweb = addOption(new BoolOption("Cobweb clutch", true));
        slimeBlock = addOption(new BoolOption("Slime block clutch", true));
        minFallDistance = addOption(new SliderOption("Min fall distance", 3, 2, 10, 1));
        setEnabled(false);
    }
}
