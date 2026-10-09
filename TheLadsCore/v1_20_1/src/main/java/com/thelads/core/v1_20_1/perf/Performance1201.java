package com.thelads.core.v1_20_1.perf;

import com.thelads.core.config.ModuleSupport;
import com.thelads.core.v1_20_1.compat.ModPresence1201;
import com.thelads.core.v1_20_1.perf.borderless.BorderlessController1201;
import com.thelads.core.v1_20_1.perf.dynamicfps.util.KeyMappingHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
        com.thelads.core.v1_20_1.embedded.EmbeddedMods1201.clientInit();
        ClientTickEvents.END_CLIENT_TICK.register(client -> BorderlessController1201.tick());
    }

    private static void dynamicFps() {
        if (ModPresence1201.loaded("dynamic_fps")) {
            ModuleSupport.registerExternal("DynamicFPS", "Dynamic FPS", "dynamic_fps", true);
            LOGGER.info("DynamicFPS: the Dynamic FPS jar is installed, Lads stands down");
        } else {
            ModuleSupport.registerBuiltIn("DynamicFPS");
            KeyMappingHandler.register();
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
}
