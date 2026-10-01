package com.thelads.core.v1_21_1.embedded;

import net.fabricmc.api.ModInitializer;
import org.slf4j.LoggerFactory;

/** What embedded mods ran from their own {@code main} entrypoint, each still standing down while its original is installed. */
public final class EmbeddedModsMain implements ModInitializer {
    @Override
    public void onInitialize() {
        if (EmbeddedMods.active("ksyxis")) LoggerFactory.getLogger("Ksyxis").info("Ksyxis: Ready to remove unneeded chunks. (embedded in Lads Core)");
    }
}
