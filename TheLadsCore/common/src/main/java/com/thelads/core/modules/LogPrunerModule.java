package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public final class LogPrunerModule extends Module {
    public static final String NAME = "LogPruner";

    public final SliderOption maxAgeDays;
    public final SliderOption maxFiles;

    public LogPrunerModule() {
        super(NAME, "Prunes and cleans old compressed Minecraft log files (.log.gz) to free up disk space.");
        maxAgeDays = addOption(new SliderOption("Max Age (Days)", 14, 1, 60, 1));
        maxFiles = addOption(new SliderOption("Max Files Kept", 20, 5, 50, 5));
        setEnabled(false);
    }

    public int getMaxAgeDays() {
        return (int) maxAgeDays.getValue();
    }

    public int getMaxFiles() {
        return (int) maxFiles.getValue();
    }
}
