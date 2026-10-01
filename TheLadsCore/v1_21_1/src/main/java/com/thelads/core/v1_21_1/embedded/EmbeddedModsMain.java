package com.thelads.core.v1_21_1.embedded;

import net.fabricmc.api.ModInitializer;

/** The "main" entrypoint half of EmbeddedMods: upstream ModInitializers, each skipped while its original jar is installed. */
public final class EmbeddedModsMain implements ModInitializer {
    @Override
    public void onInitialize() {
        if (EmbeddedMods.active("nbtac")) new com.thelads.core.v1_21_1.embedded.nbtac.NBTacFabric().onInitialize();
    }
}
