package com.thelads.core.v26_2.embedded;

import com.thelads.core.v26_2.embedded.cushions.OptimizedCushionsClient;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/**
 * Upstream mods rebuilt inside Core with no Lads module (1.4.6), one package each. Each stands down while its original
 * jar is installed, so a copy a player adds never runs twice: EmbeddedMixinPlugin skips its mixins, clientInit its setup.
 */
public final class EmbeddedMods {
    private EmbeddedMods() {}

    /** False while the original mod is installed; that copy runs instead. */
    public static boolean active(String originalModId) {
        return !FabricLoader.getInstance().isModLoaded(originalModId);
    }

    /** Called once from the Core client initializer. */
    public static void clientInit() {
        if (active("fastipping")) LoggerFactory.getLogger("FastIpPing").info("ping & connect fast!");
        if (active("optimizedcushions")) OptimizedCushionsClient.init();
    }
}
