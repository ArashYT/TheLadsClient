package com.thelads.core.v1_21_11.embedded;

import com.thelads.core.v1_21_11.embedded.cushions.OptimizedCushionsClient;
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
        if (Boolean.getBoolean("thelads.verifyAutoWorld")) {
            EmbeddedNetworkProbe.run();
            if (active("entityculling")) com.thelads.core.v1_21_11.embedded.entityculling.EntityCulling.selfTest();
            if (active("lazy_ai_pixelindiedev")) com.thelads.core.v1_21_11.embedded.lazyai.LazyAi.selfTest();
        }
        // Optimizes Cushion-Backport's entities, so only while that mod is loaded.
        if (active("optimizedcushionsbackport") && FabricLoader.getInstance().isModLoaded("cushionbackport")) OptimizedCushionsClient.init();
    }
}
