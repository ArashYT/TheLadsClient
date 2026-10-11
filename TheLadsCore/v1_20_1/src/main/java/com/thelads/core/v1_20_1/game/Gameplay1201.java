package com.thelads.core.v1_20_1.game;

import com.thelads.core.client.DamageTilt;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.ToggleSprintModule;
import com.thelads.core.v1_20_1.game.anim.NativeOldAnimations1201;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gameplay, animation and world-render modules of the 1.20.1 adapter (the 26.3 modules ported): the one place the client
 * entrypoint calls. Mixins live in theladscore.v1_20_1.game.mixins.json.
 */
public final class Gameplay1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-1.20.1");
    private static boolean qa;
    /** QA: the direction and strength the last hurt-camera tilt used (DamageTiltMixin). */
    public static volatile float lastTiltYaw, lastTiltStrength = 1;

    private Gameplay1201() {}

    /** Called once from the client entrypoint, after the shared module list was registered. */
    public static void register() {
        qa = Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null;
        // Modules this lane implements natively. The entrypoint's shared list registered a few of them as built in before they worked.
        ModuleSupport.registerBuiltIn(ToggleSprintModule.NAME, "Fullbright", "LegacySwing", DamageTilt.MODULE, NativeVerticalBob1201.MODULE,
            OldAnimationsModule.NAME, "BetterResolution");
        if (zoomOwnedByMod()) ModuleSupport.registerExternal("Zoom", "Just Zoom", "justzoom", true);
        else ModuleSupport.registerBuiltIn("Zoom");
        if (ModPresence1201.loaded("distanthorizons") && !qaFlag("allow-dh"))
            ModuleSupport.registerBuiltInLimited("BetterResolution", "It stays off while Distant Horizons is installed (both draw the world into its own target).");
        NativeCustomFov1201.register();
        GoodMcAttackSpeedReset1201.register();
        com.thelads.core.v1_20_1.game.cheats.Cheats1201.register();
        ClientTickEvents.END_CLIENT_TICK.register(Gameplay1201::tick);
        com.thelads.core.v1_20_1.game.qa.GameProbe1201.register();
        com.thelads.core.v1_20_1.game.skin.SkinLayers1201.register();
        com.thelads.core.v1_20_1.game.nea.Nea1201.register();
        com.thelads.core.v1_20_1.game.misc.Misc1201.register();
        LOGGER.info("Lads gameplay modules for 1.20.1 registered (loader: {})",
            ModPresence1201.forge() ? "Forge through Connector" : "Fabric");
    }

    private static void tick(Minecraft mc) {
        NativeVerticalBob1201.tick();
        NativeOldAnimations1201.tick(mc.player);
    }

    /** QA runs drive input themselves in windows that are never focused. */
    public static boolean qaActive() {
        return qa && gameFolderIsQa();
    }

    private static Boolean folderIsQa; // the game folder never changes: toRealPath once, not every frame (RenderScale1201 asks per frame)

    private static boolean gameFolderIsQa() {
        Boolean cached = folderIsQa;
        if (cached == null) folderIsQa = cached = gameFolderIsQa0();
        return cached;
    }

    private static boolean gameFolderIsQa0() {
        try {
            String dir = FabricLoader.getInstance().getGameDir().toRealPath().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            return dir.contains("/artifacts/verification/");
        } catch (Exception e) {
            return false;
        }
    }

    /** QA only: a flag file {@code <game folder>/lads-qa/<name>} that switches a behaviour for one test run. */
    public static boolean qaFlag(String name) {
        try {
            return qaActive() && java.nio.file.Files.exists(FabricLoader.getInstance().getGameDir().resolve("lads-qa").resolve(name));
        } catch (Exception e) {
            return false;
        }
    }

    /** Another Zoom mod (JustZoom) is installed: Lads Zoom leaves the key, FOV and scroll to it. */
    public static boolean zoomOwnedByMod() {
        return ModPresence1201.loaded("justzoom");
    }
}
