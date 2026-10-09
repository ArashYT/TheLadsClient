package com.thelads.core.v1_20_1.perf;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Runs before Minecraft's main class loads: what must be in place before the game starts logging and loading classes. */
public final class PerfPreLaunch1201 implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        AsyncLogging1201.install();
        EnumValuesHook1201.install();
    }
}
