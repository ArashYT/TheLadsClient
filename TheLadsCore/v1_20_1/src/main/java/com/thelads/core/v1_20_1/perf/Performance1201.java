package com.thelads.core.v1_20_1.perf;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.v1_20_1.perf.borderless.BorderlessController1201;
import com.thelads.core.v1_20_1.perf.connection.ConnectionTweaks1201;
import com.thelads.core.v1_20_1.perf.dynamicfps.util.HudInfoRenderer;
import com.thelads.core.v1_20_1.perf.dynamicfps.util.KeyMappingHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance, system and integration features of the 1.20.1 client. {@link #register()} is the one call the client
 * initializer makes, after its own module list: it settles which modules are Lads' own and which belong to an installed
 * original mod (plain Fabric jar or Forge jar under Connector).
 */
public final class Performance1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore-Perf");

    private Performance1201() {}

    public static void register() {
        dynamicFps();
        borderless();
        jasione();
        integrations();
        com.thelads.core.v1_20_1.feature.clumps.NativeClumps1201.register();
        ConnectionTweaks1201.register();
        DiscordScreenNames1201.register();
        PerfProbe1201.register();
        com.thelads.core.v1_20_1.embedded.EmbeddedMods1201.clientInit();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BorderlessController1201.tick();
            if (client.getOverlay() == null) EnumValuesHook1201.reportOnce();
        });
    }

    private static void dynamicFps() {
        if (ModPresence1201.loaded("dynamic_fps")) {
            ModuleSupport.registerExternal("DynamicFPS", "Dynamic FPS", "dynamic_fps", true);
            LOGGER.info("DynamicFPS: the Dynamic FPS jar is installed, Lads stands down");
        } else {
            ModuleSupport.registerBuiltIn("DynamicFPS");
            KeyMappingHandler.register();
            // Fabric API draws this on both loaders (Forgified Fabric API maps it onto Forge overlays; Forge's own Gui class never runs vanilla Gui.render).
            HudRenderCallback.EVENT.register((graphics, tickDelta) -> HudInfoRenderer.renderInfo(graphics));
            LOGGER.info("DynamicFPS: Lads' own background profiles are active");
        }
    }

    private static void borderless() {
        if (ModPresence1201.loaded("borderlesswindow")) {
            ModuleSupport.registerExternal("BorderlessFullscreen", "Borderless Window", "borderlesswindow", true);
            LOGGER.info("BorderlessFullscreen: the Borderless Window jar is installed, Lads stands down");
        } else {
            ModuleSupport.registerBuiltIn("BorderlessFullscreen");
            LOGGER.info("BorderlessFullscreen: Lads' own borderless window is active");
        }
    }

    /**
     * Engines Lads wraps instead of replacing. ExternalModSettings1201 registered them with Fabric-only detection; this settles
     * them again with Forge-aware detection, so Zero SMP (Embeddium, Forge jars) shows the right status.
     */
    private static void integrations() {
        String renderer = ModPresence1201.loaded("sodium") ? "sodium" : ModPresence1201.loaded("embeddium") ? "embeddium" : "rubidium";
        ModuleSupport.registerExternal("Performance", renderer.equals("sodium") ? "Sodium" : renderer.equals("embeddium") ? "Embeddium" : "Rubidium",
            renderer, ModPresence1201.loaded("sodium", "embeddium", "rubidium"));
        ModuleSupport.registerExternal("JEI (Just Enough Items)", "Just Enough Items", "jei", ModPresence1201.loaded("jei"));
        ModuleSupport.registerExternal("XaeroWorldmap", "Xaero's World Map", "xaeroworldmap", ModPresence1201.loaded("xaeroworldmap"));
        ModuleSupport.registerExternal("FerriteCore", "FerriteCore", "ferritecore", ModPresence1201.loaded("ferritecore"));
        // Wrapper cards with no Lads engine on 1.20.1 (same as 26.3's ExternalModSettings): the entrypoint's built-in list runs after
        // ExternalModSettings1201.register() and had turned them into "Built in" switches that do nothing.
        ModuleSupport.registerExternal("BetterStats", "Better Statistics Screen", "betterstats", ModPresence1201.loaded("betterstats"));
        ModuleSupport.registerExternal("Resourcify", "Resourcify", "resourcify", ModPresence1201.loaded("resourcify"));
        ModuleSupport.registerExternal("ModernAdvancements", "Modern Advancements", "modern-advancements", ModPresence1201.loaded("modern-advancements", "modernadvancements"));
        ModuleSupport.registerExternal("ScalableLux", "ScalableLux", "scalablelux", ModPresence1201.loaded("scalablelux"));
        ModuleSupport.registerExternal("Lithium", "Lithium", "lithium", ModPresence1201.loaded("lithium"));
        // 26.x ships Jade as source inside Core; the 1.20.1 build does not (yet), so the module follows the real mod.
        if (ModPresence1201.loaded("jade")) {
            ModuleSupport.registerExternal("Jade", "Jade", "jade", true);
        } else {
            ModuleSupport.registerUnavailable("Jade", "Lads does not bundle Jade for Minecraft 1.20.1 yet. Install the Jade mod to use it.");
        }
        // The Entity Culling switch is the 1.8.9 Core's own culling; 26.x (and now 1.20.1) cull hidden entities by themselves, with no switch,
        // unless the original Entity Culling mod is installed (then its status page is shown).
        if (ModPresence1201.loaded("entityculling")) {
            ModuleSupport.registerExternal("EntityCulling", "Entity Culling", "entityculling", true);
        } else {
            ModuleSupport.registerUnavailable("EntityCulling", "Only for Minecraft 1.8.9: this version already skips drawing hidden entities by itself.");
        }
        // Async ticks entities on the integrated server (26.x only by design: it rewrites 26.x's entity ticking). Not ported:
        // 1.20.1 modpacks run many mods whose entities and block entities are not thread safe.
        ModuleSupport.registerUnavailable("Async", "Parallel entity ticking is only available on Minecraft 26.x.");
        // Flashback Settings configures the Flashback mod, which does not exist for 1.20.1 (the Replay Mod is the 1.20.1 replay tool).
        ModuleSupport.registerUnavailable("Flashback Settings", "Flashback needs Minecraft 1.21 or newer. Use the Replay Mod on 1.20.1.");
        // Like 26.3: no Lads engine for Exordium; the module is only there while the mod is.
        if (ModPresence1201.loaded("exordium")) {
            ModuleSupport.registerExternal("Exordium", "Exordium", "exordium", true);
        } else {
            ModuleSupport.registerUnavailable("Exordium", "Lads has no HUD caching engine for Minecraft 1.20.1 yet. Install the Exordium mod to use it.");
        }
    }

    private static void jasione() {
        if (ModPresence1201.onForge()) {
            ModuleSupport.registerUnavailable("Jasione", "Under Forge the class loader is not Fabric's Knot; the Enum#values() rewrite is not available here.");
        } else if (ModPresence1201.loaded("jasione")) {
            ModuleSupport.registerExternal("Jasione", "Jasione", "jasione", true);
        } else {
            ModuleSupport.registerBuiltInRestart("Jasione");
        }
    }
}
