package com.thelads.core.v1_21_11.embedded;

import com.thelads.core.v1_21_11.embedded.quickpack.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** What embedded mods ran from their own {@code main} entrypoint, each still standing down while its original is installed. */
public final class EmbeddedModsMain implements ModInitializer {
    @Override
    public void onInitialize() {
        if (EmbeddedMods.active("ksyxis")) LoggerFactory.getLogger("Ksyxis").info("Ksyxis: Ready to remove unneeded chunks. (embedded in Lads Core)");
        if (EmbeddedMods.active("quick-pack")) ConfigManager.load(FabricLoader.getInstance().getConfigDir());
    }
}
