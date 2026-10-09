package com.thelads.core.v1_20_1.game;

import com.thelads.core.config.ModuleSupport;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gameplay, animation and world-render modules of the 1.20.1 adapter (the 26.3 modules ported): the one place the client
 * entrypoint calls. Mixins live in theladscore.v1_20_1.game.mixins.json.
 */
public final class Gameplay1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");
    private static boolean qa;

    private Gameplay1201() {}

    /** Called once from the client entrypoint, after the shared module list was registered. */
    public static void register() {
        qa = Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null;
        com.thelads.core.v1_20_1.game.skin.SkinLayers1201.register();
        com.thelads.core.v1_20_1.game.nea.Nea1201.register();
        com.thelads.core.v1_20_1.game.misc.Misc1201.register();
        LOGGER.info("Lads gameplay modules for 1.20.1 registered (loader: {})",
            ModPresence1201.forge() ? "Forge through Connector" : "Fabric");
    }

    /** QA runs drive input themselves in windows that are never focused. */
    public static boolean qaActive() {
        return qa && gameFolderIsQa();
    }

    private static boolean gameFolderIsQa() {
        try {
            String dir = FabricLoader.getInstance().getGameDir().toRealPath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            return dir.contains("/artifacts/verification/");
        } catch (Exception e) {
            return false;
        }
    }

    /** Another Zoom mod (JustZoom) is installed: Lads Zoom leaves the key, FOV and scroll to it. */
    public static boolean zoomOwnedByMod() {
        return ModPresence1201.loaded("justzoom");
    }
}
