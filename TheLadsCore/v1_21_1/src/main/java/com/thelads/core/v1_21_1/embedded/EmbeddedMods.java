package com.thelads.core.v1_21_1.embedded;

import net.fabricmc.loader.api.FabricLoader;

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
        if (active("fixbookgui")) com.thelads.core.v1_21_1.embedded.fixbookgui.FixBookGui.init();
        if (active("worldplaytime")) new com.thelads.core.v1_21_1.embedded.playtime.fabric.WorldPlayTimeFabric().onInitializeClient();
    }
}
