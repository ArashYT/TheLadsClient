package com.thelads.core.modules;

import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

public final class AutoBackupModule extends Module {
    public static final String NAME = "AutoBackup";

    public final SliderOption maxBackups;

    public AutoBackupModule() {
        super(NAME, "Automatically creates a timestamped zip backup of singleplayer worlds upon closing, retaining the newest N backups.");
        maxBackups = addOption(new SliderOption("Max Backups Kept", 5, 1, 20, 1));
        setEnabled(false);
    }

    public int getMaxBackups() {
        return (int) maxBackups.getValue();
    }
}
