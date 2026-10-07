package com.thelads.core.v26_2;

import com.thelads.core.v26_2.feature.AsyncLogging;
import com.thelads.core.v26_2.feature.EnumValuesHook;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Runs before Minecraft's main class loads: what must be in place before the game starts logging and loading classes. */
public final class LadsPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        AsyncLogging.install();
        EnumValuesHook.install();
        try {
            com.thelads.core.client.util.EssentialConfigEnforcer.enforce(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir());
        } catch (Throwable ignored) {}
    }
}
